/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 * 
 * This file is part of the "DSS - Digital Signature Services" project.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.service.ocsp;

import eu.europa.esig.dss.enumerations.RevocationOrigin;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.revocation.ocsp.OCSP;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.client.jdbc.JdbcCacheConnector;
import eu.europa.esig.dss.spi.x509.revocation.RevocationToken;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPToken;
import org.apache.commons.codec.binary.Hex;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcCacheOcspSourceTest {

	private static final Logger LOG = LoggerFactory.getLogger(JdbcCacheOcspSourceTest.class);
	
	private JdbcCacheOCSPSource ocspSource = new MockJdbcCacheOCSPSource();
	
	private JdbcDataSource dataSource = new JdbcDataSource();
	
	private OCSPToken storedRevocationToken = null;
	private Date requestTime = null;
	
	@BeforeEach
	public void setUp() throws SQLException {
		dataSource.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
		JdbcCacheConnector jdbcCacheConnector = new JdbcCacheConnector(dataSource);
		ocspSource.setJdbcCacheConnector(jdbcCacheConnector);
		assertFalse(ocspSource.isTableExists());
		ocspSource.initTable();
		assertTrue(ocspSource.isTableExists());
	}
	
	@Test
	public void test() throws Exception {
		OCSPToken revocationToken;
		
		CertificateToken certificateToken = DSSUtils.loadCertificate(new File("src/test/resources/ec.europa.eu.crt"));
		CertificateToken rootToken = DSSUtils.loadCertificate(new File("src/test/resources/CALT.crt"));
		revocationToken = ocspSource.getRevocationToken(certificateToken, rootToken);
		assertNull(revocationToken);
		
		OnlineOCSPSource onlineOCSPSource = new OnlineOCSPSource();
		ocspSource.setProxySource(onlineOCSPSource);
		ocspSource.setDefaultNextUpdateDelay(180L); // cache expiration in 180 seconds
		revocationToken = ocspSource.getRevocationToken(certificateToken, rootToken);
		assertNotNull(revocationToken);
		assertEquals(RevocationOrigin.EXTERNAL, revocationToken.getExternalOrigin());
		requestTime = new Date();

		// check real {@code findRevocation()} method behavior
		OCSPToken savedRevocationToken = ocspSource.getRevocationToken(certificateToken, rootToken);
		assertNotNull(savedRevocationToken);
		assertEquals(revocationToken.getAbbreviation(), savedRevocationToken.getAbbreviation());
		assertEquals(revocationToken.getCreationDate(), savedRevocationToken.getCreationDate());
		assertEquals(revocationToken.getDSSIdAsString(), savedRevocationToken.getDSSIdAsString());
		assertEquals(Hex.encodeHexString(revocationToken.getEncoded()), Hex.encodeHexString(savedRevocationToken.getEncoded()));
		assertEquals(Hex.encodeHexString(revocationToken.getIssuerX500Principal().getEncoded()), Hex.encodeHexString(savedRevocationToken.getIssuerX500Principal().getEncoded()));
		assertEquals(revocationToken.getNextUpdate(), savedRevocationToken.getNextUpdate());
		assertEquals(RevocationOrigin.CACHED, savedRevocationToken.getExternalOrigin());
		assertNotEquals(revocationToken.getExternalOrigin(), savedRevocationToken.getExternalOrigin());
		assertEquals(revocationToken.getProductionDate(), savedRevocationToken.getProductionDate());
		assertEquals(Hex.encodeHexString(revocationToken.getPublicKeyOfTheSigner().getEncoded()), Hex.encodeHexString(savedRevocationToken.getPublicKeyOfTheSigner().getEncoded()));
		assertEquals(revocationToken.getReason(), savedRevocationToken.getReason());
		assertEquals(revocationToken.getRelatedCertificateId(), savedRevocationToken.getRelatedCertificateId());
		assertEquals(revocationToken.getRevocationDate(), savedRevocationToken.getRevocationDate());
		assertEquals(revocationToken.getSignatureAlgorithm().getEncryptionAlgorithm().name(), savedRevocationToken.getSignatureAlgorithm().getEncryptionAlgorithm().name());
		assertEquals(revocationToken.getSourceURL(), savedRevocationToken.getSourceURL());
		assertEquals(revocationToken.getStatus(), savedRevocationToken.getStatus());
		assertEquals(revocationToken.getThisUpdate(), savedRevocationToken.getThisUpdate());
		
		// check that token can be obtained more than once
		storedRevocationToken = ocspSource.getRevocationToken(certificateToken, rootToken);
		assertNotNull(storedRevocationToken);
		assertEquals(RevocationOrigin.CACHED, storedRevocationToken.getExternalOrigin());

		// check a dummy token with the old maxUpdateDelay
		OCSPToken refreshedRevocationToken = ocspSource.getRevocationToken(certificateToken, rootToken);
		assertNotNull(refreshedRevocationToken);
		assertEquals(RevocationOrigin.CACHED, refreshedRevocationToken.getExternalOrigin());
		
		// Force refresh (1 second)
		ocspSource.setMaxNextUpdateDelay(1L);

		// wait one second
		Calendar nextSecond = Calendar.getInstance();
		nextSecond.add(Calendar.SECOND, 1);
		await().atMost(2, TimeUnit.SECONDS).until(() -> Calendar.getInstance().getTime().compareTo(nextSecond.getTime()) > 0);

		// check the dummy token with forcing one second refresh
		refreshedRevocationToken = ocspSource.getRevocationToken(certificateToken, rootToken);
		assertNotNull(refreshedRevocationToken);
		assertEquals(RevocationOrigin.EXTERNAL, refreshedRevocationToken.getExternalOrigin());

	}
	
	/**
	 * Mocked to avoid time synchronization issue between this computer time and the OCSP responder
	 * (remote server is synchronized with UTC)
	 */
	@SuppressWarnings("serial")
	private class MockJdbcCacheOCSPSource extends JdbcCacheOCSPSource {
		
		@Override
		protected RevocationToken<OCSP> findRevocation(String key, CertificateToken certificateToken,
				CertificateToken issuerCertificateToken) {
			if (storedRevocationToken == null) {
				return super.findRevocation(key, certificateToken, issuerCertificateToken);
			} else {
				LOG.info("ThisUpdate was overridden from {} to {}", storedRevocationToken.getThisUpdate(), requestTime);
				storedRevocationToken.getThisUpdate().setTime(requestTime.getTime());
				return storedRevocationToken;
			}
		}
	}
	
	@AfterEach
	public void cleanUp() throws SQLException {
		ocspSource.destroyTable();
		assertFalse(ocspSource.isTableExists());
	}
	
}
