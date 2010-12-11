/*
 * CypressFX2Biasgen.java
 *
 * Created on 23 Jan 2008
 *
 */
package ch.unizh.ini.jaer.chip.dvs320;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2Biasgen;
import de.thesycon.usbio.*;
import de.thesycon.usbio.structs.*;
import javax.swing.ProgressMonitor;
import java.io.*;
import java.math.BigInteger;
import java.util.prefs.Preferences;

/**
 * Adds functionality of cDVSTest10 retina test chip to base classes for Cypress FX2 interface.
 *
 * @author tobi
 */
public class cDVSTestHardwareInterface extends CypressFX2Biasgen {

    static Preferences prefs=Preferences.userNodeForPackage(cDVSTestHardwareInterface.class);
    
    /** The USB product ID of this device */
    static public final short PID = (short) 0x840A;
    private boolean adcEnabled=prefs.getBoolean("cDVSTestHardwareInterface.adcEnabled", true);
    private short TrackTime = (short)prefs.getInt("cDVSTestHardwareInterface.TrackTime", 50), 
            RefOnTime = (short)prefs.getInt("cDVSTestHardwareInterface.RefOnTime", 20), 
            RefOffTime = (short)prefs.getInt("cDVSTestHardwareInterface.RefOffTime", 20), 
            IdleTime = (short)prefs.getInt("cDVSTestHardwareInterface.IdleTime", 10);
    private boolean Select5Tbuffer = prefs.getBoolean("cDVSTestHardwareInterface.Select5Tbuffer", true);
    private boolean UseCalibration = prefs.getBoolean("cDVSTestHardwareInterface.UseCalibration", false);

    /** Creates a new instance of CypressFX2Biasgen */
    public cDVSTestHardwareInterface(int devNumber) {
        super(devNumber);
    }

    /** Overrides sendConfiguration to use this bias generator to format the data
     * 
     * @param biasgen the DVS320 biasgen which knows how to format the bias and bit configuration.
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
     */
    @Override
    public synchronized void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        byte[] bytes = biasgen.formatConfigurationBytes(biasgen);
        if (bytes == null) {
            log.warning("null byte array - not sending");
            return;
        }
        super.sendBiasBytes(bytes);
        if(adcEnabled){ startADC();} // TODO hack to restart ADC after sending configuration, shouldn't be necessary
    }
 
    public void setTrackTime(short trackTimeUs) {
        try {
            TrackTime = trackTimeUs;  // TODO bound values here
            sendCPLDconfiguration();
            prefs.putInt("cDVSTestHardwareInterface.TrackTime",TrackTime);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setIdleTime(short trackTimeUs) {
        try {
            IdleTime = trackTimeUs;// TODO bound values here
            sendCPLDconfiguration();
              prefs.putInt("cDVSTestHardwareInterface.IdleTime",IdleTime);
      } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setRefOnTime(short trackTimeUs) {
        try {
            RefOnTime = trackTimeUs;// TODO bound values here
            sendCPLDconfiguration();
              prefs.putInt("cDVSTestHardwareInterface.RefOnTime",RefOnTime);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setRefOffTime(short trackTimeUs) {
        try {
            RefOffTime = trackTimeUs;// TODO bound values here
            sendCPLDconfiguration();
             prefs.putInt("cDVSTestHardwareInterface.RefOffTime",RefOffTime);
         } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setSelect5Tbuffer(boolean se) {
        try {
            Select5Tbuffer = se;
            sendCPLDconfiguration();
              prefs.putBoolean("cDVSTestHardwareInterface.Select5Tbuffer",Select5Tbuffer);
       } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setUseCalibration(boolean se) {
        try {
            UseCalibration = se;
            sendCPLDconfiguration();
              prefs.putBoolean("cDVSTestHardwareInterface.UseCalibration",UseCalibration);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }
    private byte ADCchannel = 3;
    private static final int ADCchannelshift = 5;

    public void setADCchannel(byte chan) {
        try {
            if(chan<0) chan=0; else if(chan>3)chan=3;
            ADCchannel = chan;
            sendCPLDconfiguration();
              prefs.putInt("cDVSTestHardwareInterface.ADCchannel",ADCchannel);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }
    private static short ADCconfig = (short) 0x100;   //normal power mode, single ended, sequencer unused : (short) 0x908;
    private final static short ADCconfigLength = (short) 12;

    private String getBitString(short value, short nSrBits) {
        StringBuilder s = new StringBuilder();

        int k = nSrBits - 1;
        while (k >= 0) {
            int x = value & (1 << k); // start with msb
            boolean b = (x == 0); // get bit
            s.append(b ? '0' : '1'); // append to string 0 or 1, string grows with msb on left
            k--;
        } // construct big endian string e.g. code=14, s='1011'
        String bitString = s.toString();
        return bitString;
    }

    synchronized public void sendCPLDconfiguration() throws HardwareInterfaceException {
        short ADCword = (short) (ADCconfig | (getADCchannel() << ADCchannelshift));

        int nBits = 0;

        StringBuilder s = new StringBuilder();

        s.append(getBitString((short) (getIdleTime() * 15), (short) 16)); // multiplication with 15 to get from us to clockcycles
        nBits += 16;
        s.append(getBitString((short) (getRefOffTime() * 15), (short) 16)); // multiplication with 15 to get from us to clockcycles
        nBits += 16;
        s.append(getBitString((short) (getRefOnTime() * 15), (short) 16)); // multiplication with 15 to get from us to clockcycles
        nBits += 16;
        s.append(getBitString((short) (getTrackTime() * 15), (short) 16)); // multiplication with 15 to get from us to clockcycles
        nBits += 16;
        s.append(getBitString(ADCword, ADCconfigLength));
        nBits += ADCconfigLength;

        if (isUseCalibration()) {
            s.append(getBitString((short) 1, (short) 1));
        } else {
            s.append(getBitString((short) 0, (short) 1));
        }
        nBits += 1;

        if (isSelect5Tbuffer()) {
            s.append(getBitString((short) 1, (short) 1));
        } else {
            s.append(getBitString((short) 0, (short) 1));
        }
        nBits += 1;

        //s.reverse();
        //System.out.println(s);

        BigInteger bi = new BigInteger(s.toString(), 2);
        byte[] byteArray = bi.toByteArray(); // finds minimal set of bytes in big endian format, with MSB as first element
        // we need to pad out to nbits worth of bytes
        int nbytes = (nBits % 8 == 0) ? (nBits / 8) : (nBits / 8 + 1); // 8->1, 9->2
        byte[] bytes = new byte[nbytes];
        System.arraycopy(byteArray, 0, bytes, nbytes - byteArray.length, byteArray.length);

        this.sendVendorRequest(VENDOR_REQUEST_WRITE_CPLD_SR, (short) 0, (short) 0, bytes); // stops ADC running
    }

    synchronized public void startADC() throws HardwareInterfaceException {
        sendCPLDconfiguration();
        this.sendVendorRequest(VENDOR_REQUEST_RUN_ADC, (short) 1, (short) 0);
    }

    synchronized public void stopADC() throws HardwareInterfaceException {
        this.sendVendorRequest(VENDOR_REQUEST_RUN_ADC, (short) 0, (short) 0);
    }

    
    public boolean isADCEnabled(){ return adcEnabled;}
            

    public void setADCEnabled(boolean yes) throws HardwareInterfaceException {
        if (yes) {
            startADC();
        } else {
            stopADC();
        }
        this.adcEnabled=yes;
    }

    @Override
    synchronized public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        //        System.out.println("BiasgenUSBInterface.setPowerDown("+powerDown+")");
        //        if(!powerDown)
        //            setPowerDownSingle(true);
        setPowerDownSingle(powerDown);
    }

    synchronized private void setPowerDownSingle(final boolean powerDown) throws HardwareInterfaceException {

        if (gUsbIo == null) {
            throw new RuntimeException("device must be opened before sending this vendor request");
        }
        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        int result;
        //        System.out.println("sending bias bytes");
        USBIO_DATA_BUFFER dataBuffer = new USBIO_DATA_BUFFER(0); // no data, control is in setupdat
        vendorRequest.Request = VENDOR_REQUEST_SET_ARRAY_RESET;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits = 0;
        vendorRequest.Index = 0;  // meaningless for this request

        vendorRequest.Value = (short) (powerDown ? 1 : 0);  // this is the request bit, if powerDown true, send value 1, false send value 0

        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        result = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);
        if (result != de.thesycon.usbio.UsbIoErrorCodes.USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("setPowerDown: unable to send: " + UsbIo.errorText(result));
        }
        HardwareInterfaceException.clearException();

    }

    synchronized private void setChipReset(final boolean reset) throws HardwareInterfaceException {

        if (gUsbIo == null) {
            throw new RuntimeException("device must be opened before sending this vendor request");
        }
        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        int result;
        //        System.out.println("sending bias bytes");
        USBIO_DATA_BUFFER dataBuffer = new USBIO_DATA_BUFFER(0); // no data, control is in setupdat
        vendorRequest.Request = VENDOR_REQUEST_SET_ARRAY_RESET;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits = 0;
        vendorRequest.Index = 0;  // meaningless for this request

        vendorRequest.Value = (short) (reset ? 1 : 0);  // this is the request bit, if powerDown true, send value 1, false send value 0

        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        result = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);
        if (result != de.thesycon.usbio.UsbIoErrorCodes.USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("setChipReset: unable to send: " + UsbIo.errorText(result));
        }
        HardwareInterfaceException.clearException();
    }

    @Override
    synchronized public void resetTimestamps() {
        log.info(this + ".resetTimestamps(): zeroing timestamps");


        // send vendor request for device to reset timestamps
        if (gUsbIo == null) {
            throw new RuntimeException("device must be opened before sending this vendor request");
        }
        try {
            stopADC();
            setChipReset(isChipReset());
            chipReset = !isChipReset();
            startADC();
            this.sendVendorRequest(VENDOR_REQUEST_RESET_TIMESTAMPS);
        } catch (HardwareInterfaceException e) {
            log.warning("could not send vendor request to reset timestamps: " + e);
        }
    }
    private boolean chipReset = false;

    private byte[] parseHexData(String firmwareFile) throws IOException {

        byte[] fwBuffer;
        // load firmware file (this is binary file of 8051 firmware)

        log.info("reading firmware file " + firmwareFile);
        FileReader reader;
        LineNumberReader lineReader;
        String line;
        int length;
        // load firmware file (this is a lattice c file)
        try {

            reader = new FileReader(firmwareFile);
            lineReader = new LineNumberReader(reader);

            line = lineReader.readLine();
            while (!line.startsWith("xdata")) {
                line = lineReader.readLine();
            }
            int scIndex = line.indexOf(";");
            int eqIndex = line.indexOf("=");
            int index = 0;
            length = Integer.parseInt(line.substring(eqIndex + 2, scIndex));
            // log.info("File length: " + length);
            String[] tokens;
            fwBuffer = new byte[length];
            Short value;
            while (!line.endsWith("};")) {
                line = lineReader.readLine();
                tokens = line.split("0x");
                //    System.out.println(line);
                for (int i = 1; i < tokens.length; i++) {
                    value = Short.valueOf(tokens[i].substring(0, 2), 16);
                    fwBuffer[index++] = value.byteValue();
                    //   System.out.println(fwBuffer[index-1]);
                }
            }
            // log.info("index" + index);

            lineReader.close();
        } catch (IOException e) {
            close();
            log.warning(e.getMessage());
            throw new IOException("can't load binary Cypress FX2 firmware file " + firmwareFile);
        }
        return fwBuffer;
    }

    @Override
    synchronized public void writeCPLDfirmware(String svfFile) throws HardwareInterfaceException {
        byte[] bytearray;
        int status, index;
        USBIO_DATA_BUFFER dataBuffer = null;

        try {
            bytearray = this.parseHexData(svfFile);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        ProgressMonitor progressMonitor = makeProgressMonitor("Writing CPLD configuration - do not unplug", 0, bytearray.length);


        int result;
        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();


        int numChunks;

        vendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;  // this is a vendor, not generic USB, request
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice; // device (not endpoint, interface, etc) receives it
        vendorRequest.RequestTypeReservedBits = 0;    // set these bits to zero for Cypress-specific 'vendor request' rather that user defined
        vendorRequest.Request = VR_DOWNLOAD_FIRMWARE; // this is download/upload firmware request. really it is just a 'fill RAM request'
        vendorRequest.Index = 0;

        //	2) send the firmware to Control Endpoint 0
        // when sending firmware, we need to break up the loaded fimware
        //		into MAX_CONTROL_XFER_SIZE blocks
        //
        // this means:
        //	a) the address to load it to needs to be changed (VendorRequest.Value)
        //	b) need a pointer that moves through FWbuffer (pBuffer)
        //	c) keep track of remaining bytes to transfer (FWsize_left);


        //send all but last chunk
        vendorRequest.Value = 0;			//address of firmware location
        dataBuffer = new USBIO_DATA_BUFFER(MAX_CONTROL_XFER_SIZE);
        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);

        numChunks = bytearray.length / MAX_CONTROL_XFER_SIZE;  // this is number of full chunks to send
        for (int i = 0; i < numChunks; i++) {
            System.arraycopy(bytearray, i * MAX_CONTROL_XFER_SIZE, dataBuffer.Buffer(), 0, MAX_CONTROL_XFER_SIZE);
            result = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);
            if (result != USBIO_ERR_SUCCESS) {
                close();
                throw new HardwareInterfaceException("Error on downloading segment number " + i + " of CPLD firmware: " + UsbIo.errorText(result));
            }
            progressMonitor.setProgress(vendorRequest.Value);
            progressMonitor.setNote(String.format("sent %d of %d bytes of CPLD configuration", vendorRequest.Value, bytearray.length));
            vendorRequest.Value += MAX_CONTROL_XFER_SIZE;			//change address of firmware location
            if (progressMonitor.isCanceled()) {
                progressMonitor = makeProgressMonitor("Writing CPLD configuration - do not unplug", 0, bytearray.length);
            }
        }

        // now send final (short) chunk
        int numBytesLeft = bytearray.length % MAX_CONTROL_XFER_SIZE;  // remainder
        if (numBytesLeft > 0) {
            dataBuffer = new USBIO_DATA_BUFFER(numBytesLeft);
            dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
            //    vendorRequest.Index = 1; // indicate that this is the last chuck, now program CPLD
            System.arraycopy(bytearray, numChunks * MAX_CONTROL_XFER_SIZE, dataBuffer.Buffer(), 0, numBytesLeft);

            // send remaining part of firmware
            result = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);
            if (result != USBIO_ERR_SUCCESS) {
                close();
                throw new HardwareInterfaceException("Error on downloading final segment of CPLD firmware: " + UsbIo.errorText(result));
            }
        }

        vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        dataBuffer = new USBIO_DATA_BUFFER(1);

        vendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits = 0;
        vendorRequest.Request = VR_DOWNLOAD_FIRMWARE;
        vendorRequest.Index = 1;
        vendorRequest.Value = 0;

        dataBuffer.setNumberOfBytesToTransfer(1);
        status = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);

        if (status != USBIO_ERR_SUCCESS) {
            log.info(UsbIo.errorText(status));
            try {
                Thread.sleep(2000);
                this.open();
            } catch (Exception e) {
            }
        }

        vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        dataBuffer = new USBIO_DATA_BUFFER(10);

        vendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits = 0;
        vendorRequest.Request = VR_DOWNLOAD_FIRMWARE;
        vendorRequest.Index = 0;
        vendorRequest.Value = 0;

        dataBuffer.setNumberOfBytesToTransfer(10);
        status = gUsbIo.classOrVendorInRequest(dataBuffer, vendorRequest);

        if (status != USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("Unable to receive error code: " + UsbIo.errorText(status));
        }

        HardwareInterfaceException.clearException();

        // log.info("bytes transferred" + dataBuffer.getBytesTransferred());
        if (dataBuffer.getBytesTransferred() == 0) {
            //this.sendVendorRequest(VR_DOWNLOAD_FIRMWARE, (short) 0, (short) 0);
            throw new HardwareInterfaceException("Unable to program CPLD, could not get xsvf Error code");
        }
        progressMonitor.close();

        if (dataBuffer.Buffer()[1] != 0) {
            //this.sendVendorRequest(VR_DOWNLOAD_FIRMWARE, (short) 0, (short) 0);
            int dataindex = (dataBuffer.Buffer()[6] << 24) | (dataBuffer.Buffer()[7] << 16) | (dataBuffer.Buffer()[8] << 8) | (dataBuffer.Buffer()[9]);
            int algoindex = (dataBuffer.Buffer()[2] << 24) | (dataBuffer.Buffer()[3] << 16) | (dataBuffer.Buffer()[4] << 8) | (dataBuffer.Buffer()[5]);
            throw new HardwareInterfaceException("Unable to program CPLD, error code: " + dataBuffer.Buffer()[1] + " algo index: " + algoindex + " data index " + dataindex);
            // System.out.println("Unable to program CPLD, unable to program CPLD, error code: " + dataBuffer.Buffer()[1] + ", at command: " + command + " index: " + index + " commandlength " + commandlength);
        }
    }

    /** 
     * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
    our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {  // raphael: changed from private to protected, because i need to access this method
        setAeReader(new RetinaAEReader(this));
        allocateAEBuffers();

        getAeReader().startThread(3); // arg is number of errors before giving up
        startADC();
        HardwareInterfaceException.clearException();
    }
    boolean gotY = false; // TODO  hack for debugging state machine

    /**
     * @return the TrackTime
     */
    public short getTrackTime() {
        return TrackTime;
    }

    /**
     * @return the RefOnTime
     */
    public short getRefOnTime() {
        return RefOnTime;
    }

    /**
     * @return the RefOffTime
     */
    public short getRefOffTime() {
        return RefOffTime;
    }

    /**
     * @return the IdleTime
     */
    public short getIdleTime() {
        return IdleTime;
    }

    /**
     * @return the Select5Tbuffer
     */
    public boolean isSelect5Tbuffer() {
        return Select5Tbuffer;
    }

    /**
     * @return the UseCalibration
     */
    public boolean isUseCalibration() {
        return UseCalibration;
    }

    /**
     * @return the ADCchannel
     */
    public byte getADCchannel() {
        return ADCchannel;
    }

    /**
     * @return the chipReset
     */
    public boolean isChipReset() {
        return chipReset;
    }

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class RetinaAEReader extends CypressFX2.AEReader {

        public RetinaAEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }
        /** Method to translate the UsbIoBuffer for the DVS320 sensor which uses the 32 bit address space.
         *<p>
         * It has a CPLD to timestamp events and uses the CypressFX2 in slave
         * FIFO mode. 
         *<p>The DVS320 has a burst mode readout mechanism that 
         * outputs a row address,
         * then all the latched column addresses.
         *The columns are output left to right. A timestamp is only
         * meaningful at the row addresses level. Therefore
         *the board timestamps on row address, and then 
         * sends the data in the following sequence:
         * timestamp, row, col, col, col,....,timestamp,row,col,col...
         * <p>
         * Intensity information is transmitted by bit 8, which is set by the chip
         *The bit encoding of the data is as follows
         *<literal>
        Address bit	Address bit pattern
        0	LSB Y or Polarity ON=1
        1	Y1 or LSB X
        2	Y2 or X1
        3	Y3 or X2
        4	Y4 or X3
        5	Y5 or X4
        6	Y6 or X5
        7	Y7 (MSBY) or X6
        8	intensity or X7. This bit is set for a Y address if the intensity neuron has spiked. This bit is also X7 for X addreses.
        9	X8 (MSBX)
        10	Y=0, X=1
        </literal>
         *
         * The two msbs of the raw 16 bit data are used to tag the type of data, e.g. address, timestamp, or special events wrap or
         * reset host timestamps.
        <literal>
        Address             Name
        00xx xxxx xxxx xxxx	pixel address
        01xx xxxx xxxx xxxx	timestamp
        10xx xxxx xxxx xxxx	wrap
        11xx xxxx xxxx xxxx	timestamp reset
        </literal>
        
         *The msb of the 16 bit timestamp is used to signal a wrap (the actual timestamp is only 15 bits).
         * The wrapAdd is incremented when an emtpy event is received which has the timestamp bit 15
         * set to one.
         *<p>
         * Therefore for a valid event only 15 bits of the 16 transmitted timestamp bits are valid, bit 15
         * is the status bit. overflow happens every 32 ms.
         * This way, no roll overs go by undetected, and the problem of invalid wraps doesn't arise.
         *@param b the data buffer
         *@see #translateEvents
         */
        static private final byte Xmask = (byte) 0x01;
        static private final byte IntensityMask = (byte) 0x40;
        private int lasty = 0;
        private int currentts = 0;
        private int lastts = 0;

        @Override
        protected void translateEvents(UsbIoBuf b) {
            try {
                // data from cDVS is stateful. 2 bytes sent for each word of data can consist of either timestamp, y address, x address, or ADC value.
                // The type of data is determined from bits in these two bytes.

//            if(tobiLogger.isEnabled()==false) tobiLogger.setEnabled(true); //debug
                synchronized (aePacketRawPool) {
                    AEPacketRaw buffer = aePacketRawPool.writeBuffer();

                    int NumberOfWrapEvents;
                    NumberOfWrapEvents = 0;

                    byte[] buf = b.BufferMem;
                    int bytesSent = b.BytesTransferred;
                    if (bytesSent % 2 != 0) {
                        System.err.println("CypressFX2.AEReader.translateEvents(): warning: " + bytesSent + " bytes sent, which is not multiple of 2");
                        bytesSent = (bytesSent / 2) * 2; // truncate off any extra part-event
                    }

                    int[] addresses = buffer.getAddresses();
                    int[] timestamps = buffer.getTimestamps();
                    //log.info("received " + bytesSent + " bytes");
                    // write the start of the packet
                    buffer.lastCaptureIndex = eventCounter;
//                     tobiLogger.log("#packet");
                    for (int i = 0; i < bytesSent; i += 2) {
                        //   tobiLogger.log(String.format("%d %x %x",eventCounter,buf[i],buf[i+1])); // DEBUG
                        //   int val=(buf[i+1] << 8) + buf[i]; // 16 bit value of data
                        int dataword = (0xff & buf[i]) | (0xff00 & (buf[i + 1] << 8));  // data sent little endian

                        final int code = (buf[i + 1] & 0xC0) >> 6; // gets two bits at XX00 0000 0000 0000. (val&0xC000)>>>14;
                        //  log.info("code " + code);
                        switch (code) {
                            case 0: // address
                                // If the data is an address, we write out an address value if we either get an ADC reading or an x address.
                                // We also write a (fake) address if
                                // we get two y addresses in a row, which occurs when the on-chip AE state machine doesn't properly function.
                                //  Here we also read y addresses but do not write out any output address until we get either 1) an x-address, or 2)
                                // another y address without intervening x-address.
                                // NOTE that because ADC events do not have a timestamp, the size of the addresses and timestamps data are not the same.
                                // To simplify data structure handling in AEPacketRaw and AEPacketRawPool,
                                // ADC events are timestamped just like address-events. ADC events get the timestamp of the most recently preceeding address-event.
                                // NOTE2: unmasked bits are read as 1's from the hardware. Therefore it is crucial to properly mask bits.
                                if ((eventCounter >= aeBufferSize) || (buffer.overrunOccuredFlag)) {
                                    buffer.overrunOccuredFlag = true; // throw away events if we have overrun the output arrays
                                } else {
                                    if ((dataword & cDVSTest20.ADDRESS_TYPE_MASK) == cDVSTest20.ADDRESS_TYPE_ADC) {
                                        addresses[eventCounter] = dataword;
                                        timestamps[eventCounter] = currentts;  // ADC event gets last timestamp
                                        eventCounter++;
                                        //      System.out.println("ADC word: " + (dataword&cDVSTest20.ADC_DATA_MASK));
                                    } else if ((buf[i + 1] & Xmask) == Xmask) {////  received an X address, write out event to addresses/timestamps output arrays
                                        // x adddress
                                        //xadd = (buf[i] & 0xff);  //
                                        addresses[eventCounter] = (lasty << cDVSTest20.YSHIFT) | (dataword & (cDVSTest20.XMASK | cDVSTest20.POLMASK));  // combine current bits with last y address bits and send
                                        timestamps[eventCounter] = currentts; // add in the wrap offset and convert to 1us tick
                                        eventCounter++;
                                        //    log.info("received x address");
                                        gotY = false;
                                    } else {// y address
                                        // lasty = (0xFF & buf[i]); //
                                        if (gotY) {// TODO creates bogus event to see y without x. This should not normally occur.
                                            addresses[eventCounter] = (lasty << cDVSTest20.YSHIFT) + (cDVSTest20.SIZEX_TOTAL - 1 << 1);                 //(0xffff&((short)buf[i]&0xff | ((short)buf[i+1]&0xff)<<8));
                                            timestamps[eventCounter] = lastts; //*TICK_US; //add in the wrap offset and convert to 1us tick
                                            eventCounter++;
//                                        //log.warning("received at least two Y addresses consecutively");
                                        }
                                        if ((buf[i] & IntensityMask) != 0) { // intensity spike
                                            // log.info("received intensity bit");
                                            addresses[eventCounter] = cDVSTest20.INTENSITYMASK;
                                            timestamps[eventCounter] = currentts;
                                            eventCounter++;
                                        }
                                        lasty = (cDVSTest20.YMASK >>> cDVSTest20.YSHIFT) & dataword; //(0xFF & buf[i]); //
                                        gotY = true;
                                    }
                                }
                                break;
                            case 1: // timestamp
                                lastts = currentts;
                                currentts = ((0x3f & buf[i + 1]) << 8) | (buf[i] & 0xff);
                                currentts = (TICK_US * (currentts + wrapAdd));
                                //           log.info("received timestamp");
                                break;
                            case 2: // wrap
                                wrapAdd += 0x4000L;
                                NumberOfWrapEvents++;
                                //   log.info("wrap");
                                break;
                            case 3: // ts reset event
                                this.resetTimestamps();
                                //   log.info("timestamp reset");
                                break;
                        }
                    } // end for

                    buffer.setNumEvents(eventCounter);
                    // write capture size
                    buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;

                    //     log.info("packet size " + buffer.lastCaptureLength + " number of Y addresses " + numberOfY);
                    // if (NumberOfWrapEvents!=0) {
                    //System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
                    //}
                    //System.out.println("wrapAdd : "+ wrapAdd);
                } // sync on aePacketRawPool
            } catch (java.lang.IndexOutOfBoundsException e) {
                log.warning(e.toString());
            }
        }
    }
}
