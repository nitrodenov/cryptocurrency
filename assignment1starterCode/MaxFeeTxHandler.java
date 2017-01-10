import java.security.PublicKey;
import java.util.*;

public class MaxFeeTxHandler {
    private UTXOPool utxoPool;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
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

        for (Transaction possibleTx : possibleTxs) {
            possibleTransactions.add(possibleTx);
        }

        boolean haveValidTransaction = true;

        while (haveValidTransaction) {
            haveValidTransaction = false;

            for (Iterator<Transaction> iterator = possibleTransactions.listIterator(); iterator.hasNext(); ) {
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

        TreeSet<TransactionWithFee> transactionWithFees = new TreeSet<>(new Comparator<TransactionWithFee>() {
            @Override
            public int compare(TransactionWithFee o1, TransactionWithFee o2) {
                if (o1.getFee() < o2.getFee()) {
                    return -1;
                } else if (o1.getFee() == o2.getFee()) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        for (Transaction transaction : confirmedTransactions) {
            double totalInput = 0;
            double totalOutput = 0;
            for (int i = 0; i < transaction.numInputs(); i++) {
                Transaction.Input input = transaction.getInput(i);
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

                Transaction.Output output = utxoPool.getTxOutput(utxo);

                totalInput += output.value;
            }

            for (Transaction.Output output : transaction.getOutputs()) {
                totalOutput += output.value;
            }

            double diff = totalInput - totalOutput;

            transactionWithFees.add(new TransactionWithFee(diff, transaction));
        }

        int size = transactionWithFees.size();
        Transaction[] resultTx = new Transaction[size];

        int k = 0;

        for (TransactionWithFee next : transactionWithFees) {
            resultTx[k] = next.getTransaction();
            k++;
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

    private class TransactionWithFee {
        double fee;
        Transaction transaction;

        public TransactionWithFee(double fee, Transaction transaction) {
            this.fee = fee;
            this.transaction = transaction;
        }

        public double getFee() {
            return fee;
        }

        public Transaction getTransaction() {
            return transaction;
        }
    }
}
