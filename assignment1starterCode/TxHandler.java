import java.security.PublicKey;
import java.util.*;

public class TxHandler {

    private final UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        return tx != null
                && isContainedInPool(tx)
                && isValidSignature(tx)
                && isSingleUTXOinPool(tx)
                && isNonNegativeOutputValues(tx)
                && isSameValues(tx);
    }


    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
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

    //(1) all outputs claimed by {@code tx} are in the current UTXO pool,
    //чтобы совершить новую транзакцию, выход предыдущей должен быть в списке непотрачкнных
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

    //(2) the signatures on each input of {@code tx} are valid,
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

    //(3) no UTXO is claimed multiple times by {@code tx},
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

    //(4) all of {@code tx}s output values are non-negative, and
    private boolean isNonNegativeOutputValues(final Transaction tx) {
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false;
            }
        }

        return true;
    }

    //(5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
    //     values; and false otherwise.
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
