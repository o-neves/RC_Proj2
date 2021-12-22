import java.io.File;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import cnss.simulator.Node;
import ft21.FT21AbstractSenderApplication;
import ft21.FT21_AckPacket;
import ft21.FT21_DataPacket;
import ft21.FT21_FinPacket;
import ft21.FT21_UploadPacket;

public class FT21SenderGBN_DT extends FT21AbstractSenderApplication {
    private double rtt = 0;
    private double timeOut = 1000;


    static int RECEIVER = 1;
    enum State {
        BEGINNING, UPLOADING, FINISHING, FINISHED
    };
    static int DEFAULT_TIMEOUT = 1000;

    private File file;
    private RandomAccessFile raf;
    private int BlockSize, windowSize;
    private int nextPacketSeqN = 1, lastPacketSeqN;

    private FT21SenderGBN.State state;
    private int lastPacketSent;

    private Map<Integer,Integer> dataNotConfirmed;


    //windows size
    //seqN que não tiveram ack
    //Fim de timeout de um deles mandar apartir dele

    public FT21SenderGBN_DT() {
        super(true, "FT21SenderGBN");
    }


    public int initialise(int now, int node_id, Node nodeObj, String[] args) {
        super.initialise(now, node_id, nodeObj, args);

        dataNotConfirmed = new TreeMap<>();

        raf = null;
        file = new File(args[0]);
        BlockSize = Integer.parseInt(args[1]);
        windowSize = Integer.parseInt(args[2]);

        state = FT21SenderGBN.State.BEGINNING;
        lastPacketSeqN = (int) Math.ceil(file.length() / (double) BlockSize);
        System.out.println(lastPacketSeqN);

        lastPacketSent = -1;
        return 1;
    }

    public void on_clock_tick(int now) {
        boolean canSend = dataNotConfirmed.size() < windowSize || lastPacketSeqN > nextPacketSeqN;


        for (Map.Entry<Integer, Integer> entry : dataNotConfirmed.entrySet()) {
            int seqN = entry.getKey();
            int time = entry.getValue();
            if(now - time > timeOut) {
                //now é tempo atual e time é o momento em que foi enviado
                nextPacketSeqN = seqN;
                dataNotConfirmed.clear();
                break;
            }
        }

        if (state != FT21SenderGBN.State.FINISHED && canSend)
            sendNextPacket(now);
    }

    private void sendNextPacket(int now) {
        switch (state) {
            case BEGINNING:
                super.sendPacket(now, RECEIVER, new FT21_UploadPacket(file.getName()));
                break;
            case UPLOADING:
                super.sendPacket(now, RECEIVER, readDataPacket(file, nextPacketSeqN));
                dataNotConfirmed.put(nextPacketSeqN,now);
                nextPacketSeqN++;

                break;
            case FINISHING:
                super.sendPacket(now, RECEIVER, new FT21_FinPacket(nextPacketSeqN));
                break;
            case FINISHED:
        }

        lastPacketSent = now;
    }

    @Override
    public void on_receive_ack(int now, int client, FT21_AckPacket ack) {
        switch (state) {
            case BEGINNING:
                state = FT21SenderGBN.State.UPLOADING;
            case UPLOADING:

                if (nextPacketSeqN > lastPacketSeqN)
                    state = FT21SenderGBN.State.FINISHING;
                lastPacketSent = -1;
                for (Map.Entry<Integer, Integer> e : new LinkedHashMap<Integer, Integer>(dataNotConfirmed).entrySet()){
                    //e.getValue() é o momento em que o pack foi adicionado
                    if(e.getKey() == ack.cSeqN){
                        rtt = now - e.getValue();
                        timeOut = timeOut * 0.875 + rtt * 0.125;
                    }
                    if(e.getKey() <= ack.cSeqN) dataNotConfirmed.remove(e.getKey());
                }
                break;
            case FINISHING:
                super.log(now, "All Done. Transfer complete...");
                super.printReport(now);
                state = FT21SenderGBN.State.FINISHED;
                return;
            case FINISHED:
        }
    }

    private FT21_DataPacket readDataPacket(File file, int seqN) {
        try {
            if (raf == null)
                raf = new RandomAccessFile(file, "r");

            raf.seek(BlockSize * (seqN - 1));
            byte[] data = new byte[BlockSize];
            int nbytes = raf.read(data);
            return new FT21_DataPacket(seqN, data, nbytes);
        } catch (Exception x) {
            throw new Error("Fatal Error: " + x.getMessage());
        }
    }

}
