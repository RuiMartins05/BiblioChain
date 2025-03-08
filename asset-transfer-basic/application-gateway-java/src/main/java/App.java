/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.Hash;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.SubmittedTransaction;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

public final class App {
	private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
	private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mychannel");
	private static final String PUBLICATION_CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "basic");

	// Path to crypto materials.
	private static final Path CRYPTO_PATH = Paths.get("../../test-network/organizations/peerOrganizations/org1.example.com");
	// Path to user certificate.
	private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/signcerts"));
	// Path to user private key directory.
	private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/keystore"));
	// Path to peer tls certificate.
	private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers/peer0.org1.example.com/tls/ca.crt"));

	// Gateway peer end point.
	private static final String PEER_ENDPOINT = "localhost:7051";
	private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

	private final Contract publicationContract;
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public static void main(final String[] args) throws Exception {
		/* There is significant overhead associated with establishing gRPC connections, so this connection should be
		 retained by the application and used for all interactions with the Fabric Gateway.
		 The gRPC client connection should be shared by all Gateway connections to this endpoint.

		 In order to maintain security of any private data used in transactions, the application should connect to
		 a Fabric Gateway belonging to the same organization as the client identity. If the client identity’s
		 organization does not host any gateways, then a trusted gateway in another organization should be used.
		 */
		ManagedChannel channel = newGrpcConnection();

		Gateway.Builder gateWaybuilder = Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .hash(Hash.SHA256)
                .connection(channel);

		try (Gateway gateway = gateWaybuilder.connect()) {
			new App(gateway).run();
		} finally {
			channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	private static ManagedChannel newGrpcConnection() throws IOException {
		var credentials = TlsChannelCredentials.newBuilder()
				.trustManager(TLS_CERT_PATH.toFile())
				.build();
		return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
				.overrideAuthority(OVERRIDE_AUTH)
				.build();
	}

	private static Identity newIdentity() throws IOException, CertificateException {
		try (var certReader = Files.newBufferedReader(getFirstFilePath(CERT_DIR_PATH))) {
			var certificate = Identities.readX509Certificate(certReader);
			return new X509Identity(MSP_ID, certificate);
		}
	}

	private static Signer newSigner() throws IOException, InvalidKeyException {
		try (var keyReader = Files.newBufferedReader(getFirstFilePath(KEY_DIR_PATH))) {
			var privateKey = Identities.readPrivateKey(keyReader);
			return Signers.newPrivateKeySigner(privateKey);
		}
	}

	private static Path getFirstFilePath(Path dirPath) throws IOException {
		try (var keyFiles = Files.list(dirPath)) {
			return keyFiles.findFirst().orElseThrow();
		}
	}

	public App(final Gateway gateway) {
		// Get a network instance representing the channel where the smart contract is deployed.
		var network = gateway.getNetwork(CHANNEL_NAME);

		publicationContract = network.getContract(PUBLICATION_CHAINCODE_NAME);
	}

	public void run() throws GatewayException, CommitException {

		initLedger();

		getAllPublications();

		checkPublicationsExistenceFlow();

		createPublicationFlow();

		deletePublicationFlow();

		getPublicationHistoryFlow();

		changePublicationTitleAsync();

	}

	private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n------ Init Ledger ------");

		publicationContract.submitTransaction("initLedger");

		System.out.println("Transaction committed successfully");
	}


	private void getAllPublications() throws GatewayException {
		System.out.println("\n------ Get All Publications ------");

		byte[] result = publicationContract.evaluateTransaction("getAll");

		System.out.println("Result: " + prettyJson(result));
	}

	private void checkPublicationsExistenceFlow() {
		String existingId = "publication1";
		String nonExistingId = "randomId";
		existsById(existingId);
		existsById(nonExistingId);
	}

	private void createPublicationFlow() {
		String id = "publication3";
		createPublication(id, "Ethereum Whitpaper");
		existsById(id);
	}

	private void deletePublicationFlow() {
		String id = "publication3";
		deletePublication(id);
		existsById(id);
	}

	private void getPublicationHistoryFlow() throws GatewayException {
		getPublicationHistory("publication3");
	}

	private void getPublicationHistory(final String id) throws GatewayException {
		System.out.println("\n------ Get History for Publication '" + id + "' ------");


		byte[] result = publicationContract.evaluateTransaction("getHistory", id);
		System.out.println("result: " + prettyJson(result));

	}

	private void createPublication(final String id, final String title) {
		System.out.println("\n------ Create Publication with Id '" + id + "' ------");

		try {
			publicationContract.submitTransaction("createPublication", id, title);
		} catch (EndorseException | SubmitException | CommitStatusException e) {
			e.printStackTrace(System.out);
			System.out.println("Transaction ID: " + e.getTransactionId());
		} catch (CommitException e) {
			e.printStackTrace(System.out);
			System.out.println("Transaction ID: " + e.getTransactionId());
			System.out.println("Status code: " + e.getCode());
		}


		System.out.println("Transaction committed successfully");
	}

	private void deletePublication(final String id) {
		System.out.println("\n------ Delete Publication with Id '" + id + "' ------");

        try {
            publicationContract.submitTransaction("deletePublication", id);
		} catch (EndorseException | SubmitException | CommitStatusException e) {
			e.printStackTrace(System.out);
			System.out.println("Transaction ID: " + e.getTransactionId());
		} catch (CommitException e) {
			e.printStackTrace(System.out);
			System.out.println("Transaction ID: " + e.getTransactionId());
			System.out.println("Status code: " + e.getCode());
		}

    }

	private boolean existsById(final String id) {
		System.out.println("\n------ Checking If Publication '" + id + "' Exists ------");

		try {
			byte[] byteResult = publicationContract.evaluateTransaction("existsById", id);
			boolean result = jsonToBoolean(byteResult);
			System.out.println("result: " + result);
			return result;
		} catch (GatewayException e) {
			System.err.println("Error while checking publication existence");
			System.err.println("Message: " + e.getMessage());
		}

		return false;
	}

	private void changePublicationTitleAsync() throws EndorseException, SubmitException, CommitStatusException {
		System.out.println("\n------ Change Publication Title Asynchronously ------");

		final String id = "publication1";
		final String newTitle = "The DAO";

		SubmittedTransaction commit = publicationContract.newProposal("updatePublication")
				.addArguments(id, newTitle)
				.build()
				.endorse()
				.submitAsync();

		System.out.println("Successfully submitted transaction to change title of Publication '" + id + "' to " + "'The DAO'");
		System.out.println("Waiting for transaction commit");

		var status = commit.getStatus();
		if (!status.isSuccessful()) {
			throw new RuntimeException("Transaction " + status.getTransactionId() +
					" failed to commit with status code " + status.getCode());
		}

		byte[] result = commit.getResult();

		System.out.println("Result: " + prettyJson(result));
	}

	private String prettyJson(final byte[] json) {
		return prettyJson(new String(json, StandardCharsets.UTF_8));
	}

	private boolean jsonToBoolean(final byte[] json) {
		return Boolean.parseBoolean(prettyJson(json).trim());
	}

	private String prettyJson(final String json) {
		var parsedJson = JsonParser.parseString(json);
		return gson.toJson(parsedJson);
	}

}
