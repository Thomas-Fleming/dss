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
package eu.europa.esig.dss.cookbook.example.snippets;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.JKSSignatureToken;
import eu.europa.esig.dss.utils.Utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore.PasswordProtection;
import java.util.List;

public class JKSSnippet {

    public static void main(String[] args) throws IOException {

        // tag::demo[]

        try (InputStream is = new FileInputStream("src/main/resources/keystore.jks");
             JKSSignatureToken token = new JKSSignatureToken(is, new PasswordProtection("dss-password".toCharArray()))) {

            List<DSSPrivateKeyEntry> keys = token.getKeys();
            for (DSSPrivateKeyEntry entry : keys) {
                System.out.println(entry.getCertificate().getCertificate());
            }

            ToBeSigned toBeSigned = new ToBeSigned("Hello world".getBytes());
            SignatureValue signatureValue = token.sign(toBeSigned, DigestAlgorithm.SHA256, keys.get(0));

            System.out.println("Signature value : " + Utils.toBase64(signatureValue.getValue()));
        }

        // end::demo[]
    }

}
