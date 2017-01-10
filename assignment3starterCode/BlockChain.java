// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockChain {

    public static final int CUT_OFF_AGE = 10;
    private ArrayList<Node> heads;

    private Map<ByteArrayWrapper, Node> nodes;
    private  TransactionPool transactionPool;
    private int maxHeight;
    private Node maxHeightBlock;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        // IMPLEMENT THIS
        Transaction coinbaseTx = genesisBlock.getCoinbase();
        UTXO utxo = new UTXO(coinbaseTx.getHash(), 0);

        UTXOPool utxoPool = new UTXOPool();
        utxoPool.addUTXO(utxo, coinbaseTx.getOutput(0));

        Node genesisNode = new Node(null, genesisBlock, utxoPool);
        heads = new ArrayList<>();
        heads.add(genesisNode);
        nodes = new HashMap<>();
        nodes.put(new ByteArrayWrapper(genesisBlock.getHash()), genesisNode);
        transactionPool = new TransactionPool();
        maxHeight = 1;
        maxHeightBlock = genesisNode;
    }

    /**
     * Get the maximum height block
     */
    public Block getMaxHeightBlock() {
        // IMPLEMENT THIS
        if (maxHeightBlock == null) {
            return null;
        }

        return maxHeightBlock.block;
    }

    /**
     * Get the UTXOPool for mining a new block on top of max height block
     */
    public UTXOPool getMaxHeightUTXOPool() {
        // IMPLEMENT THIS
        if (maxHeightBlock == null) {
            return null;
        }

        return maxHeightBlock.getUtxoPoolCopy();
    }

    /**
     * Get the transaction pool to mine a new block
     */
    public TransactionPool getTransactionPool() {
        // IMPLEMENT THIS
        return transactionPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * <p>
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     *
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        // IMPLEMENT THIS
        byte[] prevBlockHash = block.getPrevBlockHash();
        if (prevBlockHash == null) {
            return false;
        }

        Node parentNode = nodes.get(new ByteArrayWrapper(prevBlockHash));
        if (parentNode == null) {
            return false;
        }

        UTXOPool parentUTXOPool = parentNode.getUtxoPoolCopy();

        TxHandler txHandler = new TxHandler(parentUTXOPool);
        Transaction[] transactions = block.getTransactions().toArray(new Transaction[0]);

        Transaction[] validTransactions = txHandler.handleTxs(transactions);

        if (validTransactions.length != transactions.length) {
            return false;
        }

        Transaction coinbaseTx = new Transaction(block.getCoinbase());
        UTXO coinbaseUTXO = new UTXO(coinbaseTx.getHash(), 0);

        UTXOPool newUtxoPool = txHandler.getUTXOPool();
        newUtxoPool.addUTXO(coinbaseUTXO, coinbaseTx.getOutput(0));

        if (newUtxoPool == null) {
            return false;
        }

        for (Transaction transaction : block.getTransactions()) {
            transactionPool.removeTransaction(transaction.getHash());
        }

        Node newNode = new Node(parentNode, block, newUtxoPool);
        nodes.put(new ByteArrayWrapper(block.getHash()), newNode);
        if (newNode.height > maxHeight) {
            maxHeightBlock = newNode;
            maxHeight = newNode.height;
        }

        if (maxHeight - heads.get(0).height > CUT_OFF_AGE) {
            ArrayList<Node> newHeads = new ArrayList<>();
            for (Node head : heads) {
                for (Node child : head.children) {
                    newHeads.add(child);
                }
                nodes.remove(new ByteArrayWrapper(head.block.getHash()));
            }
            heads = newHeads;
        }

        return true;
    }

    /**
     * Add a transaction to the transaction pool
     */
    public void addTransaction(Transaction tx) {
        // IMPLEMENT THIS
        if (transactionPool == null) {
            return;
        }

        transactionPool.addTransaction(tx);
    }

    class Node {
        Node parent;
        List<Node> children;
        Block block;
        UTXOPool utxoPool;
        int height;

        public Node(Node parent, Block block, UTXOPool utxoPool) {
            this.parent = parent;
            this.block = block;
            this.utxoPool = utxoPool;
            children = new ArrayList<>();

            if (parent != null) {
                parent.children.add(this);
                height = parent.height + 1;
            } else {
                height = 1;
            }
        }

        public UTXOPool getUtxoPoolCopy() {
            return new UTXOPool(utxoPool);
        }
    }
}