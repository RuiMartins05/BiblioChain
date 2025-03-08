package org.example;


import com.owlike.genson.Genson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

@Contract(name = "basic")
@Default
public class PublicationService implements ContractInterface {

    private final Genson genson = new Genson();

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void initLedger(final Context ctx) {
        put(ctx, new Publication("publication1", "BiblioChain Thesis"));
        put(ctx, new Publication("publication2", "Bitcoin Whitepaper"));
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getAll(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        List<Publication> queryResults = new ArrayList<>();

        // To retrieve all assets from the ledger use getStateByRange with empty startKey & endKey.
        // Giving empty startKey & endKey is interpreted as all the keys from beginning to end.
        // As another example, if you use startKey = 'asset0', endKey = 'asset9' ,
        // then getStateByRange will retrieve asset with keys between asset0 (inclusive) and asset9 (exclusive) in lexical order.
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");

        for (KeyValue result: results) {
            Publication asset = genson.deserialize(result.getStringValue(), Publication.class);
            System.out.println(asset);
            queryResults.add(asset);
        }

        return genson.serialize(queryResults);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean existsById(final Context ctx, final String id) {
        String assetJSON = ctx.getStub().getStringState(id);
        return (assetJSON != null && !assetJSON.isEmpty());
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Publication createPublication(final Context ctx, final String id, final String title) {

        if (existsById(ctx, id)) {
            String errorMessage = String.format("Publication %s already exists", id);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, PublicationErrors.ALREADY_EXISTS.toString());
        }

        return put(ctx, new Publication(id, title));
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Publication updatePublication(final Context ctx, final String id, final String title) {

        if (!existsById(ctx, id)) {
            String errorMessage = String.format("Publication %s does not exist", id);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, PublicationErrors.NOT_FOUND.toString());
        }

        return put(ctx, new Publication(id, title));
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void deletePublication(final Context ctx, final String id) {
        if (!existsById(ctx, id)) {
            String errorMessage = String.format("Publication %s does not exist", id);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, PublicationErrors.NOT_FOUND.toString());
        }

        ctx.getStub().delState(id);
    }

    @Transaction(intent =  Transaction.TYPE.EVALUATE)
    public String getHistory(final Context ctx, String id) {
        QueryResultsIterator<KeyModification> historyIter = ctx.getStub().getHistoryForKey(id);
        List<String> history = new ArrayList<>();

        for (KeyModification modification : historyIter) {
            String entry = String.format(
                    "TxId: %s, Value: %s, Timestamp: %s, IsDeleted: %b",
                    modification.getTxId(),
                    new String(modification.getValue(), StandardCharsets.UTF_8),
                    modification.getTimestamp(),
                    modification.isDeleted()
            );
            history.add(entry);
            System.out.println("History Entry: " + entry);
        }

        return genson.serialize(history);
    }

    private Publication put(final Context ctx, final Publication publication) {
        String sortedJson = genson.serialize(publication);
        ctx.getStub().putStringState(publication.getId(), sortedJson);

        return publication;
    }



}
