import java.security.PublicKey;
import java.util.*;

public class TxHandlerSubmited {

    private final UTXOPool utxoPool;

    public TxHandlerSubmited(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    public boolean isValidTx(Transaction tx) {
        return tx != null
                && isContainedInPool(tx)
                && isValidSignature(tx)
                && isSingleUTXOinPool(tx)
                && isNonNegativeOutputValues(tx)
                && isSameValues(tx);
    }


    public Transaction[] handleTxs(Transaction[] possibleTxs) {
     List<Transaction> confirmedTransactions = new ArrayList<>();
        List<Transaction> possibleTransactions = new ArrayList<>();
        
        for (int i = 0; i < possibleTxs.length; i++) {
            possibleTransactions.add(possibleTxs[i]);
        }

        boolean haveValidTransaction = true;

        while (haveValidTransaction) {
            haveValidTransaction = false;

            for (Iterator<Transaction> iterator = possibleTransactions.listIterator(); iterator.hasNext();) {
                Transaction transaction = iterator.next();
                if (isValidTx(transaction)) {
                    removeOldUTXO(transaction);
                    addNewUTXO(transaction);

                    confirmedTransactions.add(transaction);

                    iterator.remove();
                    haveValidTransaction = true;
                }
            }
        }

        int size = confirmedTransactions.size();
        Transaction[] resultTx = new Transaction[size];

        for (int i = 0; i < size; i++) {
            resultTx[i] = confirmedTransactions.get(i);
        }

        return resultTx;
    }

    private boolean isContainedInPool(final Transaction tx) {
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!utxoPool.contains(utxo)) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidSignature(final Transaction tx) {
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output txOutput = utxoPool.getTxOutput(utxo);

            PublicKey publicKey = txOutput.address;
            byte[] message = tx.getRawDataToSign(i);
            byte[] signature = input.signature;

            boolean isValid = Crypto.verifySignature(publicKey, message, signature);

            if (!isValid) {
                return false;
            }
        }

        return true;
    }

    private boolean isSingleUTXOinPool(final Transaction tx) {
        Set<UTXO> seenUTXO = new HashSet<>();
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!seenUTXO.add(utxo)) {
                return false;
            }
        }

        return true;
    }

    private boolean isNonNegativeOutputValues(final Transaction tx) {
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameValues(Transaction tx) {
        double totalIn = 0;
        double totalOut = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            Transaction.Output txOutput = utxoPool.getTxOutput(utxo);
            totalIn += txOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            totalOut += output.value;
        }

        return totalIn >= totalOut;
    }

    private void removeOldUTXO(final Transaction transaction) {
        for (int j = 0; j < transaction.numInputs(); j++) {
            Transaction.Input input = transaction.getInput(j);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(utxo);
        }
    }

    private void addNewUTXO(final Transaction transaction) {
        for (int j = 0; j < transaction.numOutputs(); j++) {
            UTXO utxo = new UTXO(transaction.getHash(), j);
            Transaction.Output output = transaction.getOutput(j);
            utxoPool.addUTXO(utxo, output);
        }
    }
}

