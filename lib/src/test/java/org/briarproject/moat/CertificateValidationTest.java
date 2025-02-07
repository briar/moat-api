package org.briarproject.moat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import static org.briarproject.moat.MoatApi.validateCertificateChain;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class CertificateValidationTest {

	private final X509Certificate authorityCert, intermediate1Cert, intermediate2Cert,
			leaf1Cert, leaf2Cert;
	private final X500Principal authorityPrincipal;
	private final X500Principal intermediate1Principal;
	private final X500Principal intermediate2Principal;
	private final X500Principal leaf1Principal;
	private final PublicKey authorityPublicKey, intermediate1PublicKey, intermediate2PublicKey;
	private final boolean[] authorityUniqueId, intermediate1UniqueId, intermediate2UniqueId,
			leaf1UniqueId;

	public CertificateValidationTest() {
		authorityCert = Mockito.mock(X509Certificate.class);
		intermediate1Cert = Mockito.mock(X509Certificate.class);
		intermediate2Cert = Mockito.mock(X509Certificate.class);
		leaf1Cert = Mockito.mock(X509Certificate.class);
		leaf2Cert = Mockito.mock(X509Certificate.class);

		authorityPrincipal = Mockito.mock(X500Principal.class);
		intermediate1Principal = Mockito.mock(X500Principal.class);
		intermediate2Principal = Mockito.mock(X500Principal.class);
		leaf1Principal = Mockito.mock(X500Principal.class);
		X500Principal leaf2Principal = Mockito.mock(X500Principal.class);

		authorityPublicKey = Mockito.mock(PublicKey.class);
		intermediate1PublicKey = Mockito.mock(PublicKey.class);
		intermediate2PublicKey = Mockito.mock(PublicKey.class);

		authorityUniqueId = new boolean[]{false, false, false};
		intermediate1UniqueId = new boolean[]{false, false, true};
		intermediate2UniqueId = new boolean[]{false, true, false};
		leaf1UniqueId = new boolean[]{false, true, true};
		boolean[] leaf2UniqueId = new boolean[]{true, false, false};

		boolean[] keyUsageDigitalSignatures = new boolean[]{true};
		boolean[] keyUsageSigningCertificates =
				new boolean[]{true, false, false, false, false, true};

		when(authorityCert.getSubjectX500Principal()).thenReturn(authorityPrincipal);
		when(intermediate1Cert.getSubjectX500Principal()).thenReturn(intermediate1Principal);
		when(intermediate2Cert.getSubjectX500Principal()).thenReturn(intermediate2Principal);
		when(leaf1Cert.getSubjectX500Principal()).thenReturn(leaf1Principal);
		when(leaf2Cert.getSubjectX500Principal()).thenReturn(leaf2Principal);

		when(authorityCert.getPublicKey()).thenReturn(authorityPublicKey);
		when(intermediate1Cert.getPublicKey()).thenReturn(intermediate1PublicKey);
		when(intermediate2Cert.getPublicKey()).thenReturn(intermediate2PublicKey);

		when(authorityCert.getSubjectUniqueID()).thenReturn(authorityUniqueId);
		when(intermediate1Cert.getSubjectUniqueID()).thenReturn(intermediate1UniqueId);
		when(intermediate2Cert.getSubjectUniqueID()).thenReturn(intermediate2UniqueId);
		when(leaf1Cert.getSubjectUniqueID()).thenReturn(leaf1UniqueId);
		when(leaf2Cert.getSubjectUniqueID()).thenReturn(leaf2UniqueId);

		when(authorityPrincipal.getName()).thenReturn("authority");
		when(intermediate1Principal.getName()).thenReturn("intermediate1");
		when(intermediate2Principal.getName()).thenReturn("intermediate2");
		when(leaf1Principal.getName()).thenReturn("leaf1");
		when(leaf2Principal.getName()).thenReturn("leaf2");

		when(intermediate1Cert.getKeyUsage()).thenReturn(keyUsageSigningCertificates);
		when(intermediate2Cert.getKeyUsage()).thenReturn(keyUsageSigningCertificates);
		when(leaf1Cert.getKeyUsage()).thenReturn(keyUsageDigitalSignatures);
		when(leaf2Cert.getKeyUsage()).thenReturn(keyUsageDigitalSignatures);

		when(intermediate1Cert.getBasicConstraints()).thenReturn(1);
		when(intermediate2Cert.getBasicConstraints()).thenReturn(0);
		when(leaf1Cert.getBasicConstraints()).thenReturn(-1);
		when(leaf2Cert.getBasicConstraints()).thenReturn(-1);
	}

	@Test
	public void testRejectsEmptyCertificateChain() {
		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[0], authorityCert));
	}

	@Test
	public void testRejectsExpiredCertificate() throws CertificateException {
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);
		doThrow(CertificateExpiredException.class).when(leaf1Cert).checkValidity(); // Too old

		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[]{leaf1Cert}, authorityCert));
	}

	@Test
	public void testRejectsNotYetValidCertificate() throws CertificateException {
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);
		doThrow(CertificateNotYetValidException.class).when(leaf1Cert).checkValidity(); // Too new

		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[]{leaf1Cert}, authorityCert));
	}

	@Test
	public void testRejectsWrongIssuerId() {
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(intermediate1UniqueId); // Mismatch

		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[]{leaf1Cert}, authorityCert));
	}

	@Test
	public void testRejectsWrongIssuerPrincipal() {
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(intermediate1Principal); // Mismatch
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);

		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[]{leaf1Cert}, authorityCert));
	}

	@Test
	public void testRejectsInvalidSignature() throws Exception {
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);
		doThrow(SignatureException.class).when(leaf1Cert).verify(authorityPublicKey); // Invalid

		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[]{leaf1Cert}, authorityCert));
	}

	@Test
	public void testAcceptsLeafWithoutIntermediate() throws Exception {
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);

		validateCertificateChain(new X509Certificate[]{leaf1Cert}, authorityCert);

		verify(leaf1Cert, times(1)).verify(authorityPublicKey);
	}

	@Test
	public void testRejectsIntermediateInLeafPosition() {
		when(intermediate1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(intermediate1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);

		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[]{intermediate1Cert}, authorityCert));
	}

	@Test
	public void testRejectsLeafInIntermediatePosition() {
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);
		when(leaf2Cert.getIssuerX500Principal()).thenReturn(leaf1Principal);
		when(leaf2Cert.getIssuerUniqueID()).thenReturn(leaf1UniqueId);

		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[]{leaf2Cert, leaf1Cert},
						authorityCert));
	}

	@Test
	public void testAcceptsLeafWithIntermediateWithBasicConstraintsZero() throws Exception {
		when(intermediate1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(intermediate1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(intermediate1Principal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(intermediate1UniqueId);

		validateCertificateChain(new X509Certificate[]{leaf1Cert, intermediate1Cert},
				authorityCert);

		verify(intermediate1Cert, times(1)).verify(authorityPublicKey);
		verify(leaf1Cert, times(1)).verify(intermediate1PublicKey);
	}

	@Test
	public void testAcceptsLeafWithIntermediateWithBasicConstraintsOne() throws Exception {
		when(intermediate2Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(intermediate2Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(intermediate2Principal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(intermediate2UniqueId);

		validateCertificateChain(new X509Certificate[]{leaf1Cert, intermediate2Cert},
				authorityCert);

		verify(intermediate2Cert, times(1)).verify(authorityPublicKey);
		verify(leaf1Cert, times(1)).verify(intermediate2PublicKey);
	}

	@Test
	public void testRejectsChainLongerThanBasicConstraints() throws Exception {
		// Intermediate 2's basic constraints don't allow it to sign an intermediate certificate
		when(intermediate2Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(intermediate2Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);
		when(intermediate1Cert.getIssuerX500Principal()).thenReturn(intermediate2Principal);
		when(intermediate1Cert.getIssuerUniqueID()).thenReturn(intermediate2UniqueId);
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(intermediate1Principal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(intermediate1UniqueId);

		assertThrows(CertificateException.class, () ->
				validateCertificateChain(new X509Certificate[]{leaf1Cert, intermediate1Cert,
						intermediate2Cert}, authorityCert));
	}

	@Test
	public void testAcceptsLeafWithTwoIntermediates() throws Exception {
		when(intermediate1Cert.getIssuerX500Principal()).thenReturn(authorityPrincipal);
		when(intermediate1Cert.getIssuerUniqueID()).thenReturn(authorityUniqueId);
		when(intermediate2Cert.getIssuerX500Principal()).thenReturn(intermediate1Principal);
		when(intermediate2Cert.getIssuerUniqueID()).thenReturn(intermediate1UniqueId);
		when(leaf1Cert.getIssuerX500Principal()).thenReturn(intermediate2Principal);
		when(leaf1Cert.getIssuerUniqueID()).thenReturn(intermediate2UniqueId);

		validateCertificateChain(new X509Certificate[]{leaf1Cert, intermediate2Cert,
				intermediate1Cert}, authorityCert);

		verify(intermediate1Cert, times(1)).verify(authorityPublicKey);
		verify(intermediate2Cert, times(1)).verify(intermediate1PublicKey);
		verify(leaf1Cert, times(1)).verify(intermediate2PublicKey);
	}
}
