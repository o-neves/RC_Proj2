package ft21;
public class FT21_UploadPacket2 extends FT21Packet {
    public final String filename;
    public final int optional_data_field1;
    public final int optional_data_field2;
    public FT21_UploadPacket(String filename, int optional_data_field1, int optional_da
        super(PacketType.UPLOAD);
        super.putByte(2 * Integer.BYTES );
        super.putInt( optional_data_field1 );
        super.putInt( optional_data_field2 );
        super.putString(filename);
        this.filename = filename;
        this.optional_data_field1 = optional_data_field1;
        this.optional_data_field2 = optional_data_field2;
    }
    public String toString() {
        return String.format("UPLOAD<%s, %d, %d>", filename, optional_data_field1, opti
    }
}