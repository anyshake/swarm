/*
 * This file is part of the ORFEUS Java Library.
 *
 * Copyright (C) 2004 Anthony Lomax <anthony@alomax.net www.alomax.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

 /*
 * SeedLinkConnection.java
 *
 * Created on 05 April 2004, 09:55
 *
 * 20120719 - changed by Kevin Frechette <k.frechette@isti.com> to support offsets other than 64 bytes (e.g. rtserve.iris.washington.edu)
 *
 */
package nl.knmi.orfeus.seedlink.client;

/**
 *
 * @author Anthony Lomax
 */
import nl.knmi.orfeus.seedlink.*;

import edu.iris.Fissures.seed.container.*;
import edu.iris.Fissures.seed.exception.*;

import edu.sc.seis.seisFile.mseed.DataRecord;
import edu.sc.seis.seisFile.mseed.SeedRecord;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;

import java.util.*;

/**
 *
 * Class to manage a connection to a SeedLink server using a Socket.
 *
 * See nl.knmi.orfeus.SLClient for an example of how to create and use this SeedLinkConnection object. A new SeedLink application can be created by
 * subclassing SLClient, or by creating a new class and invoking the methods of SeedLinkConnection.
 *
 * @see nl.knmi.orfeus.SLClient
 * @see java.net.Socket
 *
 *
 *
 */
public class SeedLinkConnection {

    /**
     * UTF-8
     */
    public static final Charset UTF_8;

    static {
        UTF_8 = getCharset("UTF-8", null);
    }

    /**
     * Get the character set.
     *
     * @param charsetName the character set name.
     * @param defaultCharset the default character set or null for the default character set of this Java virtual machine.
     * @return the character set for the specified character set name or the default character set if none.
     */
    public static Charset getCharset(String charsetName,
            Charset defaultCharset) {
        Charset charset = defaultCharset;
        try {
            charset = Charset.forName(charsetName);
        } catch (Exception ex) {
            if (charset == null) {
                charset = Charset.defaultCharset();
            }
        }
        return charset;
    }

    /**
     * Get the string from the XML bytes.
     *
     * @param bytes the XML bytes.
     * @return the string.
     */
    public static String getString(byte[] bytes) {
        int length = 0;
        for (; length < bytes.length; length++) {
            if (bytes[length] == 0) {
                break;
            }
        }
        String s = new String(bytes, 0, length, UTF_8);
        return s;
    }

    // constants
    /**
     * URI/URL prefix for seedlink servers ("seedlnk://")
     */
    public static final String SEEDLINK_PROTOCOL_PREFIX = "seedlink://";
    /**
     * The station code used for uni-station mode
     */
    protected static final String UNISTATION = "UNI";
    /**
     * The network code used for uni-station mode
     */
    protected static final String UNINETWORK = "XX";
    /**
     * Default size for buffer to hold responses from server.
     */
    protected static final int DFT_READBUF_SIZE = 1024;
    /**
     * Character used for delimiting timestamp strings in the statefile.
     */
    protected static char QUOTE_CHAR = '"';
    // publically accessable (get/set) parameters
    /**
     * The host:port of the SeedLink server.
     */
    protected String sladdr = null;
    /**
     * Interval to send keepalive/heartbeat (seconds).
     */
    protected int keepalive = 0;
    /**
     * Network timeout (seconds).
     */
    protected int netto = 120;
    /**
     * Network reconnect delay (seconds).
     */
    protected int netdly = 30;
    /**
     * Logging object.
     */
    protected SLLog sllog = null;
    /**
     * String containing concatination of contents of last terminated set of INFO packets
     */
    protected String infoString = "";
    /**
     * File name for storing state information.
     */
    protected String statefile = null;
    /**
     * Flag to control last packet time usage.
     *
     * if true, begin_time is appended to DATA command
     *
     *
     */
    protected boolean lastpkttime = false;
    // protected parameters
    /**
     * Vector of SLNetStation objects.
     */
    protected Vector streams = new Vector();
    /**
     * Beginning of time window.
     */
    // 20050415 AJL changed from Btime to String
    protected String begin_time = null;
    /**
     * End of time window.
     */
    // 20050415 AJL changed from Btime to String
    protected String end_time = null;
    /**
     * Flag to control resuming with sequence numbers.
     */
    protected boolean resume = true;
    /**
     * Flag to indicate multistation mode.
     */
    protected boolean multistation = false;
    /**
     * Flag to indicate dial-up mode.
     */
    protected boolean dialup = false;
    /**
     * Flag to control connection termination.
     */
    protected boolean terminateFlag = false;
    /**
     * ID of the remote SeedLink server.
     */
    protected String server_id = null;
    /**
     * Version of the remote SeedLink server
     */
    protected float server_version = 0.0f;
    /**
     * INFO level to request.
     */
    protected String infoRequestString = null;
    /**
     * The network socket.
     */
    protected java.net.Socket socket = null;
    /**
     * The network socket InputStream.
     */
    protected InputStream socketInputStream = null;
    /**
     * The network socket OutputStream.
     */
    protected OutputStream socketOutputStream = null;
    /**
     * Persistent state information.
     */
    protected SLState state = null;
    /**
     * String to store INFO packet contents.
     */
    protected StringBuffer infoStrBuf = new StringBuffer();

    /**
     *
     * Creates a new instance of SeedLinkConnection.
     *
     * @param sllog an SLLoc object to control info and error message logging.
     *
     */
    public SeedLinkConnection(SLLog sllog) {

        this.state = new SLState();
        if (sllog != null) {
            this.sllog = sllog;
        } else {
            this.sllog = new SLLog();
        }

    }

    /**
     *
     * Returns connection state of the connection socket.
     *
     * @return true if connected, false if not connected or socket is not initialized
     *
     */
    public boolean isConnected() {

        return (socket != null && socket.isConnected());

    }

    /**
     *
     * Returns the SLState state object.
     *
     * @return the SLState state object
     *
     */
    public SLState getState() {

        return (state);

    }

    /**
     *
     * Sets the SLLog logging object.
     *
     * @param sllog an SLLoc object to control info and error message logging.
     *
     */
    public void setLog(SLLog sllog) {

        if (sllog != null) {
            this.sllog = sllog;
        }

    }

    /**
     *
     * Returns the SLLog logging object.
     *
     * @return the SLLoc object to control info and error message logging.
     *
     */
    public SLLog getLog() {

        return (sllog);

    }

    /**
     *
     * Sets the network timeout (seconds).
     *
     * @param netto the network timeout in seconds.
     *
     */
    public void setNetTimout(int netto) {

        this.netto = netto;

    }

    /**
     *
     * Returns the network timeout (seconds).
     *
     * @return the network timeout in seconds.
     *
     */
    public int getNetTimout() {

        return (netto);

    }

    /**
     *
     * Sets interval to send keepalive/heartbeat (seconds).
     *
     * @param keepalive the interval to send keepalive/heartbeat in seconds.
     *
     */
    public void setKeepAlive(int keepalive) {

        this.keepalive = keepalive;

    }

    /**
     *
     * Returns the interval to send keepalive/heartbeat (seconds).
     *
     * @return the interval to send keepalive/heartbeat in seconds.
     *
     */
    public int getKeepAlive() {

        return (keepalive);

    }

    /**
     *
     * Sets the network reconnect delay (seconds).
     *
     * @param netdly the network reconnect delay in seconds.
     *
     */
    public void setNetDelay(int netdly) {

        this.netdly = netdly;

    }

    /**
     *
     * Returns the network reconnect delay (seconds).
     *
     * @return the network reconnect delay in seconds.
     *
     */
    public int getNetDelay() {

        return (netdly);

    }

    /**
     *
     * Sets the host:port of the SeedLink server.
     *
     * @param sladdr the host:port of the SeedLink server.
     *
     */
    public void setSLAddress(String sladdr) {

        if (sladdr.startsWith(SEEDLINK_PROTOCOL_PREFIX)) {
            sladdr = sladdr.substring(SEEDLINK_PROTOCOL_PREFIX.length());
        }

        this.sladdr = sladdr;

    }

    /**
     *
     * Sets a specified start time for beginning of data transmission .
     *
     * @param if true, beginning time of last packet recieved for each station is appended to DATA command on resume.
     *
     */
    public void setLastpkttime(boolean lastpkttime) {

        this.lastpkttime = lastpkttime;

    }

    /**
     *
     * Sets begin_time for initiation of continuous data transmission.
     *
     * @param startTimeStr start time in in SeedLink string format: "year,month,day,hour,minute,second".
     *
     */
    // 20050415 AJL added to support continuous data transfer from a time in the past
    public void setBeginTime(String startTimeStr) {

        if (startTimeStr != null) {
            this.begin_time = new String(startTimeStr);
        } else {
            this.begin_time = null;
        }

    }

    /**
     *
     * Sets end_time for termitiation of data transmission.
     *
     * @param endTimeStr start time in in SeedLink string format: "year,month,day,hour,minute,second".
     *
     */
    // 20071204 AJL added to support windowed data transfer
    public void setEndTime(String endTimeStr) {

        if (endTimeStr != null) {
            this.end_time = new String(endTimeStr);
        } else {
            this.end_time = null;
        }

    }

    /**
     *
     * Sets terminate flag, closes connection and clears state as soon as possible
     *
     *
     */
    public void terminate() {

        terminateFlag = true;

    }

    /**
     *
     * Returns the host:port of the SeedLink server.
     *
     * @return the host:port of the SeedLink server.
     *
     */
    public String getSLAddress() {

        return (sladdr);

    }

    /**
     *
     * Returns a copy of the Vector of SLNetStation objects.
     *
     * @return a copy of the Vector of SLNetStation objects.
     *
     */
    public Vector getStreams() {

        return ((Vector) streams.clone());

    }

    /**
     *
     * Returns the results of the last INFO request.
     *
     * @return concatination of contents of last terminated set of INFO packets
     *
     */
    public String getInfoString() {

        return (infoString);

    }

    /**
     *
     * Creates an info String from a String Buffer
     *
     * @param strBuf the buffer to convert to an INFO String.
     *
     * @return the INFO Sting.
     */
    protected String createInfoString(StringBuffer strBuf) {

        int start = 0;
        while ((start = strBuf.indexOf("><", start)) > 0) {
            strBuf.replace(start, start + 2, ">\n<");
        }

        return (strBuf.toString().trim());

    }

    /**
     *
     * Check this SeedLinkConnection description has valid parameters.
     *
     * @return true if pass and false if problems were identified.
     *
     */
    protected boolean checkslcd() {

        boolean retval = true;

        if (streams.size() < 1 && infoRequestString == null) {
            sllog.log(true, 0, "[" + sladdr + "] stream chain AND info type are empty");
            retval = false;
        }

        int ndx = 0;
        if (sladdr == null) {
            sllog.log(false, 1, "[" + sladdr + "] [" + sladdr + "] server address is empty");
            retval = false;
        } else if ((ndx = sladdr.indexOf(':')) < 1 || sladdr.length() < ndx + 2) {
            sllog.log(true, 0, "[" + sladdr + "] host address: [" + sladdr + "] is not in `[hostname]:port' format");
            retval = false;
        }

        return (retval);
    }    // End of sl_checkslconn

    /**
     *
     * Read a list of streams and selectors from a file and add them to the stream chain for configuring a multi-station connection.
     *
     * If 'defselect' is not null it will be used as the default selectors for entries will no specific selectors indicated.
     *
     * The file is expected to be repeating lines of the form:
     * <PRE>
     *   <NET> <STA> [selectors]
     * </PRE> For example:
     * <PRE>
     * # Comment lines begin with a '#' or '*'
     * GE ISP  BH?.D
     * NL HGN
     * MN AQU  BH?  HH?
     * </PRE>
     *
     * @param streamfile name of file containing list of streams and slectors.
     * @param defselect default selectors.
     *
     * @return the number of streams configured.
     *
     * @exception SeedLinkException on error.
     *
     */
    public int readStreamList(String streamfile, String defselect) throws SeedLinkException {

        int addret;

        // Open the stream list file
        BufferedReader buffReader = null;
        StreamTokenizer streamTkz = null;
        try {
            buffReader = new BufferedReader(new FileReader(streamfile));
            streamTkz = new StreamTokenizer(buffReader);
        } catch (Exception e) {
            String message = "[" + sladdr + "] " + e + ": opening stream list file: " + streamfile;
            sllog.log(true, 0, message);
            throw (new SeedLinkException(message));
        }

        sllog.log(false, 1, "reading stream list from " + streamfile);

        streamTkz.wordChars('.', '.');
        streamTkz.wordChars('?', '?');
        streamTkz.eolIsSignificant(true);
        streamTkz.commentChar('#');
        streamTkz.commentChar('*');

        // AJL 20080815 - Bug fixes to read stream tokens that contain numbers
        streamTkz.ordinaryChars('0', '9');
        streamTkz.wordChars('0', '9');

        int linecount = 0;
        int stacount = 0;

        try {

            while (streamTkz.ttype != StreamTokenizer.TT_EOF) {

                linecount++;

                String net = null;
                String station = null;
                String selectors = null;

                boolean dataline = false;

                streamTkz.nextToken();
                //System.out.println("readStreamList: <" + streamTkz.sval + "><" + streamTkz.nval + ">");
                if (streamTkz.ttype == StreamTokenizer.TT_EOF) {
                    break;
                }
                if (streamTkz.ttype == StreamTokenizer.TT_WORD) {
                    net = streamTkz.sval;
                    dataline = true;
                    streamTkz.nextToken();
                    //System.out.println("readStreamList: <" + streamTkz.sval + "><" + streamTkz.nval + ">");
                    if (streamTkz.ttype == StreamTokenizer.TT_WORD) {
                        station = streamTkz.sval;
                        streamTkz.nextToken();
                        //System.out.println("readStreamList: <" + streamTkz.sval + "><" + streamTkz.nval + ">");
                        if (streamTkz.ttype == StreamTokenizer.TT_WORD) {   // selectors present
                            selectors = "";
                            /* 20111125 AJL - replaced do/while with while
                            do {
                            selectors += " " + streamTkz.sval;
                            streamTkz.nextToken();
                            //System.out.println("readStreamList: <" + streamTkz.sval + "><" + streamTkz.nval + ">");
                            } while (streamTkz.ttype == StreamTokenizer.TT_WORD); */
                            while (streamTkz.ttype == StreamTokenizer.TT_WORD) {
                                selectors += " " + streamTkz.sval;
                                streamTkz.nextToken();
                                //System.out.println("readStreamList: <" + streamTkz.sval + "><" + streamTkz.nval + ">");
                            }
                            ;
                        }
                    }
                }
                // skip to next line
                while (streamTkz.ttype != StreamTokenizer.TT_EOL && streamTkz.ttype != StreamTokenizer.TT_EOF) {
                    streamTkz.nextToken();
                    //System.out.println("readStreamList: <" + streamTkz.sval + "><" + streamTkz.nval + ">");
                }
                if (!dataline) {
                    continue;
                }

                if (net == null) {
                    sllog.log(true, 0, "invalid or missing network string at line " + linecount + " of stream list file: " + streamfile);
                    continue;
                }
                if (station == null) {
                    sllog.log(true, 0, "invalid or missing station string line " + linecount + " of stream list file: " + streamfile);
                    continue;
                }

                // Add this stream to the stream chain
                if (selectors != null) {
                    addret = addStream(net, station, selectors, -1, null);
                    stacount++;
                } else {
                    addret = addStream(net, station, defselect, -1, null);
                    stacount++;
                }

            }
            if (stacount == 0) {
                sllog.log(true, 0, "[" + sladdr + "] no streams defined in " + streamfile);
            } else {
                sllog.log(false, 2, "[" + sladdr + "] Read " + stacount + " streams from " + streamfile);
            }

        } catch (IOException e) {
            String message = "[" + sladdr + "] " + e + ": reaading stream list file: " + streamfile;
            sllog.log(true, 0, message);
            throw (new SeedLinkException(message));
        } finally {
            try {
                buffReader.close();
            } catch (Exception e) {
                ;
            }
        }

        return (stacount);

    }              // End of read_streamlist()

    /**
     *
     * Parse a string of streams and selectors and add them to the stream chain for configuring a multi-station connection.
     *
     * The string should be of the following form: "stream1[:selectors1],stream2[:selectors2],..."
     *
     * For example:
     * <PRE>
     * "IU_KONO:BHE BHN,GE_WLF,MN_AQU:HH?.D"
     * </PRE>
     *
     * @param streamlist list of streams and slectors.
     * @param defselect default selectors.
     *
     * @return the number of streams configured.
     *
     * @exception SeedLinkException on error.
     *
     */
    public int parseStreamlist(String streamlist, String defselect) throws SeedLinkException {

        int stacount = 0;

        // Parse the streams and selectors
        StringTokenizer strTkz = new StringTokenizer(streamlist, ",");

        while (strTkz.hasMoreTokens()) {

            String net = null;
            String station = null;
            String staselect = null;

            boolean configure = true;

            try {

                String streamToken = strTkz.nextToken();
                StringTokenizer reqTkz = new StringTokenizer(streamToken, ":");
                String reqToken = reqTkz.nextToken();
                StringTokenizer netStaTkz = new StringTokenizer(reqToken, "_");

                // Fill in the NET and STA fields
                if (netStaTkz.countTokens() != 2) {
                    sllog.log(true, 0, "[" + sladdr + "] not in NET_STA format: " + reqToken);
                    configure = false;
                    ;
                } else {
                    // First token, should be a network code
                    net = netStaTkz.nextToken();
                    if (net.length() < 1) {
                        sllog.log(true, 0, "[" + sladdr + "] not in NET_STA format: " + reqToken);
                        configure = false;
                        ;
                    } else {
                        // Second token, should be a station code
                        station = netStaTkz.nextToken();
                        if (station.length() < 1) {
                            sllog.log(true, 0, "[" + sladdr + "] not in NET_STA format: " + reqToken);
                            configure = false;
                            ;
                        }
                    }

                    if (reqTkz.hasMoreTokens()) {   // Selectors were included
                        // Second token of reqTkz, should be selectors
                        staselect = reqTkz.nextToken();
                        if (staselect.length() < 1) {
                            sllog.log(true, 0, "[" + sladdr + "] empty selector: " + reqToken);
                            configure = false;
                            ;
                        }
                    } else {    // If no specific selectors, use the default
                        staselect = defselect;
                    }

                    // Add this to the stream chain
                    if (configure) {
                        try {
                            addStream(net, station, staselect, -1, null);
                            stacount++;
                        } catch (SeedLinkException e) {
                            throw (e);
                        }
                    }
                }

            } catch (NoSuchElementException e) {
                ;
            }

        }

        if (stacount == 0) {
            sllog.log(true, 0, "[" + sladdr + "] no streams defined in stream list");
        } else if (stacount > 0) {
            sllog.log(false, 2, "parsed " + stacount + " streams from stream list");
        }

        return (stacount);

    }              // End of sl_parse_streamlist()

    /**
     *
     * Add a new stream entry to the stream chain for the given net/station parameters. If the stream entry already exists do nothing and return 1.
     * Also sets the multistation flag to true.
     *
     * @param net network code.
     * @param station station code.
     * @param selectors selectors for this net/station, null if none.
     * @param seqnum SeedLink sequence number of last packet received, -1 to start at the next data.
     * @param timestamp SeedLink time stamp in SEED "year,day-of-year,hour,minute,second" format for last packet received, null for none.
     *
     * @return 0 if successfully added, 1 if an entry for network and station already exists.
     *
     * @exception SeedLinkException on error.
     *
     */
    protected int addStream(String net, String station, String selectors, int seqnum, String timestamp) throws SeedLinkException {

        // Sanity, check for a uni-station mode entry
        if (streams.size() > 0) {
            SLNetStation stream = (SLNetStation) streams.elementAt(0);
            if (stream.net.equals(UNINETWORK) && stream.station.equals(UNISTATION)) {
                String message = "[" + sladdr + "] addStream called, but uni-station mode already configured!";
                sllog.log(true, 0, message);
                throw (new SeedLinkException(message));
            }
        }

        // Search the stream chain to see if net/station/selector already present
        for (int i = 0; i < streams.size(); i++) {
            SLNetStation stream = (SLNetStation) streams.elementAt(i);
            //if (stream.net.equals(net) && stream.station.equals(station) && stream.selectors.equals(selectors))
            //	return(1);	// stream already exists in the chain
            if (stream.net.equals(net) && stream.station.equals(station)) {
                return (stream.appendSelectors(selectors)); // stream already exists in the chain, append selectors
            }
        }

        // Add new stream
        SLNetStation newstream = new SLNetStation(net, station, selectors, seqnum, timestamp);
        streams.addElement(newstream);

        multistation = true;

        return (0);

    }   // End of addstream()

    /**
     *
     * Set the parameters for a uni-station mode connection for the given SLCD struct. If the stream entry already exists, overwrite the previous
     * settings. Also sets the multistation flag to 0 (false).
     *
     * @param selectors selectors for this net/station, null if none.
     * @param seqnum SeedLink sequence number of last packet received, -1 to start at the next data.
     * @param timestamp SeedLink time stamp in SEED "year,day-of-year,hour,minute,second" format for last packet received, null for none.
     *
     * @exception SeedLinkException on error.
     *
     */
    public void setUniParams(String selectors, int seqnum, String timestamp) throws SeedLinkException {

        // Sanity, check for a multi-station mode entry
        if (streams.size() > 0) {
            SLNetStation stream = (SLNetStation) streams.elementAt(0);
            if (!stream.net.equals(UNINETWORK) || !stream.station.equals(UNISTATION)) {
                String message = "[" + sladdr + "] setUniParams called, but multi-station mode already configured!";
                sllog.log(true, 0, message);
                throw (new SeedLinkException(message));
            }
        }

        // Add new stream
        SLNetStation newstream = new SLNetStation(UNINETWORK, UNISTATION, selectors, seqnum, timestamp);
        streams.addElement(newstream);

        multistation = false;

    }   // End of sl_setuniparams()

    /**
     *
     * Set the state file and recover state.
     *
     * @param statefile path and name of statefile.
     *
     * @return the number of stream chains recovered.
     *
     * @exception SeedLinkException on error.
     *
     */
    public int setStateFile(String statefile) throws SeedLinkException {

        this.statefile = statefile;
        return (recoverState(statefile));

    }

    /**
     *
     * Recover the state file and put the sequence numbers and time stamps into the pre-existing stream chain entries.
     *
     * @param statefile path and name of statefile.
     *
     * @return the number of stream chains recovered.
     *
     * @exception SeedLinkException on error.
     *
     */
    public int recoverState(String statefile) throws SeedLinkException {

        // open the state file
        BufferedReader buffReader = null;
        StreamTokenizer streamTkz = null;
        try {
            buffReader = new BufferedReader(new FileReader(statefile));
            streamTkz = new StreamTokenizer(buffReader);
        } catch (IOException ioe) {
            sllog.log(true, 0, "[" + sladdr + "] cannot open state file: " + ioe);
            return (0);
        } catch (Exception e) {
            String message = "[" + sladdr + "] " + e + ": opening state file: " + statefile;
            sllog.log(true, 0, message);
            throw (new SeedLinkException(message));
        }

        // recover the state
        sllog.log(false, 1, "[" + sladdr + "] recovering connection state from state file: " + statefile);

        streamTkz.commentChar('#');
        streamTkz.commentChar('*');
        streamTkz.eolIsSignificant(true);
        streamTkz.quoteChar(QUOTE_CHAR);

        int linecount = 0;
        int stacount = 0;

        try {

            while (streamTkz.ttype != StreamTokenizer.TT_EOF) {

                linecount++;

                String net = null;
                String station = null;
                int seqnum = -1;
                String timeStr = "";

                streamTkz.nextToken();
                if (streamTkz.ttype == StreamTokenizer.TT_EOF) {
                    break;
                }
                if (streamTkz.ttype == StreamTokenizer.TT_WORD) {
                    net = streamTkz.sval;
                    streamTkz.nextToken();
                    if (streamTkz.ttype == StreamTokenizer.TT_WORD) {
                        station = streamTkz.sval;
                        streamTkz.nextToken();
                        if (streamTkz.ttype == StreamTokenizer.TT_NUMBER) {
                            seqnum = (int) Math.round(streamTkz.nval);
                            streamTkz.nextToken();
                            if (streamTkz.ttype == QUOTE_CHAR) {
                                timeStr = streamTkz.sval;
                                streamTkz.nextToken();
                            }
                        }
                    }
                }
                while (streamTkz.ttype != StreamTokenizer.TT_EOL && streamTkz.ttype != StreamTokenizer.TT_EOF) {
                    streamTkz.nextToken();
                }

                // check for completeness of read
                if (timeStr.equals("")) {
                    sllog.log(true, 0, "error parsing line " + linecount + " of state file");
                    continue;
                } else if (timeStr.equals("null")) {
                    continue;
                }

                // Search for a matching net/station in the stream chain
                SLNetStation stream = null;
                for (int i = 0; i < streams.size(); i++) {
                    stream = (SLNetStation) streams.elementAt(i);
                    if (stream.net.equals(net) && stream.station.equals(station)) {
                        break;	// found
                    }
                    stream = null;
                }
                // update net/station entry in the stream chain
                if (stream != null) {
                    stream.seqnum = seqnum;
                    if (timeStr != null) {
                        try {
                            stream.btime = new Btime(timeStr);
                            stacount++;
                        } catch (Exception e) {
                            sllog.log(true, 0, "parsing timestamp in line " + linecount + " of state file: " + e);
                        }
                    }
                }

            }

            if (stacount == 0) {
                sllog.log(true, 0, "[" + sladdr + "] no matching streams found in " + statefile);
            } else {
                sllog.log(false, 2, "[" + sladdr + "] recoverd state for  " + stacount + " streams in " + statefile);
            }

        } catch (IOException e) {
            String message = "[" + sladdr + "] " + e + ": reaading state  file: " + statefile;
            sllog.log(true, 0, message);
            throw (new SeedLinkException(message));
        } finally {
            try {
                buffReader.close();
            } catch (Exception e) {
                ;
            }
        }

        return (stacount);

    }

    /**
     *
     * Save all current s equence numbers and time stamps into the given state file.
     *
     * @param statefile path and name of statefile.
     *
     * @return the number of stream chains saved.
     *
     * @exception SeedLinkException on error.
     *
     */
    public int saveState(String statefile) throws SeedLinkException {

        // open the state file
        BufferedWriter buffWriter = null;
        try {
            buffWriter = new BufferedWriter(new FileWriter(statefile));
        } catch (IOException ioe) {
            sllog.log(true, 0, "[" + sladdr + "] cannot open state file: " + ioe);
        } catch (Exception e) {
            String message = "[" + sladdr + "] " + e + ": opening state file: " + statefile;
            sllog.log(true, 0, message);
            throw (new SeedLinkException(message));
        }

        sllog.log(false, 2, "[" + sladdr + "] saving connection state to state file");

        int stacount = 0;
        try {
            // Loop through the stream chain
            for (int i = 0; i < streams.size(); i++) {
                // get stream (should be only stream present)
                SLNetStation curstream = (SLNetStation) streams.elementAt(i);
                buffWriter.write(curstream.net + " " + curstream.station + " " + curstream.seqnum
                        + " " + QUOTE_CHAR + curstream.btime + QUOTE_CHAR + "\n");
            }

        } catch (IOException e) {
            String message = "[" + sladdr + "] " + e + ": writing state file: " + statefile;
            sllog.log(true, 0, message);
            throw (new SeedLinkException(message));
        } finally {
            try {
                buffWriter.close();
            } catch (Exception e) {
                ;
            }
        }

        return (stacount);

    }

    protected SLPacket doTerminate() {

        sllog.log(false, 0, "[" + sladdr + "] terminating collect loop");
        disconnect();
        //state.state = SLState.SL_DOWN;  // AJL added
        // state.state = SLState.SL_DATA; // libslink error?
        state = new SLState();  // AJL added AJL 20040526
        infoRequestString = null;   // AJL added AJL 20040526
        infoStrBuf = new StringBuffer();   // AJL added AJL 20040526
        return (SLPacket.SLTERMINATE);  // AJL added AJL 20040526

    }

    /**
     *
     * Manage a connection to a SeedLink server based on the values given in this SeedLinkConnection, and to collect data.
     *
     * Designed to run in a tight loop at the heart of a client program, this function will return every time a packet is received.
     *
     * @return an SLPacket when something is received.
     * @return null when the connection was closed by the server or the termination sequence completed.
     *
     * @exception SeedLinkException on error.
     *
     */
    public SLPacket collect() throws SeedLinkException {

        terminateFlag = false;

        // Check if the infoRequestString was set
        if (infoRequestString != null) {
            state.query_mode = SLState.INFO_QUERY;
        }

        // If the connection is not up check this SeedLinkConnection and reset the timing variables
        if (socket == null || !socket.isConnected()) {
            if (!checkslcd()) {
                String message = "[" + sladdr + "] problems with the connection description";
                sllog.log(true, 0, message);
                throw (new SeedLinkException(message));
            }
            state.previous_time = Util.getCurrentTime();  // Initialize timing base
            state.netto_trig = -1;	   // Init net timeout trigger to reset state
            state.keepalive_trig = -1;	   // Init keepalive trigger to reset state
        }

        // Start the primary loop
        int npass = 0;
        while (true) {

            sllog.log(false, 5, "[" + sladdr + "] primary loop pass " + npass);
            npass++;

            //we are terminating (abnormally!)
            if (terminateFlag) {
                return (doTerminate());
            }

            // not terminating
            if (socket == null || !socket.isConnected()) {
                state.state = SLState.SL_DOWN;
            }

            // Check for network timeout
            if (state.state == SLState.SL_DATA && netto > 0 && state.netto_trig > 0) {
                sllog.log(false, 0, "[" + sladdr + "] network timeout (" + netto + "s), reconnecting in " + netdly + "s");
                disconnect();
                state.state = SLState.SL_DOWN;
                state.netto_trig = -1;
                state.netdly_trig = -1;
            }

            // Check if a keepalive packet needs to be sent
            if (state.state == SLState.SL_DATA && !state.expect_info && keepalive > 0 && state.keepalive_trig > 0) {
                sllog.log(false, 2, "[" + sladdr + "] sending: keepalive request");
                try {
                    sendInfoRequest("ID", 3);
                    state.query_mode = SLState.KEEP_ALIVE_QUERY;
                    state.expect_info = true;
                    state.keepalive_trig = -1;
                } catch (SeedLinkException e) { // SeedLink version does not support INFO requests
                    ;
                } catch (IOException ioe) {	// I/O error, assume link is down
                    sllog.log(false, 0, "[" + sladdr + "] I/O error, reconnecting in " + netdly + "s");
                    disconnect();
                    state.state = SLState.SL_DOWN;
                }
            }

            // Check if an in-stream INFO request needs to be sent
            if (state.state == SLState.SL_DATA && !state.expect_info && infoRequestString != null) {
                try {
                    sendInfoRequest(infoRequestString, 1);
                    state.query_mode = SLState.INFO_QUERY;
                    state.expect_info = true;
                } catch (SeedLinkException e) {	// SeedLink version does not support INFO requests
                    state.query_mode = SLState.NO_QUERY;
                } catch (IOException ioe) {	// I/O error, assume link is down
                    sllog.log(false, 0, "[" + sladdr + "] I/O error, reconnecting in " + netdly + "s");
                    disconnect();
                    state.state = SLState.SL_DOWN;
                }
                infoRequestString = null;
            }

            // Throttle the loop while delaying
            if (state.state == SLState.SL_DOWN && state.netdly_trig > 0) {
                Util.sleep(500);
            }

            // Connect to remote SeedLink server
            if (state.state == SLState.SL_DOWN && state.netdly_trig == 0) {
                try {
                    connect();
                    state.state = SLState.SL_UP;
                } catch (Exception e) {
                    sllog.log(true, 0, e.toString());
                }
                state.netto_trig = -1;
                state.netdly_trig = -1;
            }

            // Negotiate/configure the connection
            if (state.state == SLState.SL_UP) {

                // Send query if a query is set,
                //   stream configuration will be done only after query is fully returned
                if (infoRequestString != null /*&& streams.size() < 1*/) {
                    try {
                        sendInfoRequest(infoRequestString, 1);
                        state.query_mode = SLState.INFO_QUERY;
                        state.expect_info = true;
                    } catch (SeedLinkException e) {	// SeedLink version does not support INFO requests
                        sllog.log(false, 1, "[" + sladdr + "] SeedLink version does not support INFO requests");
                        state.query_mode = SLState.NO_QUERY;
                        state.expect_info = false;
                    } catch (IOException ioe) {	// I/O error, assume link is down
                        sllog.log(true, 0, "[" + sladdr + "] I/O error, reconnecting in " + netdly + "s");
                        disconnect();
                        state.state = SLState.SL_DOWN;
                    }
                    infoRequestString = null;
                } else if (!state.expect_info) {
                    try {
                        configLink();
                        state.recptr = 0;	// initialize the data buffer pointers
                        state.sendptr = 0;
                        state.state = SLState.SL_DATA;
                    } catch (Exception e) {
                        sllog.log(true, 0, "[" + sladdr + "] negotiation with remote SeedLink failed: " + e);
                        disconnect();
                        state.state = SLState.SL_DOWN;  // AJL added
                        state.netdly_trig = -1;
                    }
                    state.expect_info = false;
                }

            }

            // Process data in our buffer and then read incoming data
            if (state.state == SLState.SL_DATA || (state.expect_info && !(state.state == SLState.SL_DOWN))) {

                // AJL 20040610 serious BUG in slibslink ???  moved into while loop
                //boolean sendpacket = true;
                // Process data in buffer
                while (state.packetAvailable()) {

                    SLPacket slpacket = null;
                    boolean sendpacket = true;

                    // Check for an INFO packet
                    if (state.packetIsInfo()) {

                        boolean terminator = (state.databuf[state.sendptr + SLPacket.SLHEADSIZE - 1] != '*');
                        if (!state.expect_info) {
                            sllog.log(true, 0, "[" + sladdr + "] unexpected INFO packet received, skipping");
                        } else {
                            if (terminator) {
                                state.expect_info = false;
                            }
                            // Keep alive packets are not returned
                            if (state.query_mode == SLState.KEEP_ALIVE_QUERY) {
                                //System.out.println("conn got KEEP: > ");
                                sendpacket = false;
                                if (!terminator) {
                                    sllog.log(true, 0, "[" + sladdr + "] non-terminated keep-alive packet received!?!");
                                } else {
                                    sllog.log(false, 2, "[" + sladdr + "] keepalive packet received");
                                }
                            } else {
                                slpacket = state.getPacket();
                                //System.out.println("conn got INFO: > " + slpacket.getSequenceNumber());
                                // construct info String
                                int type = slpacket.getType();

                                // 20120719 - changed by Kevin Frechette <k.frechette@isti.com> to support offsets other than 64 bytes (e.g. rtserve.iris.washington.edu)
                                //if (type == SLPacket.TYPE_SLINF) {
                                //    infoStrBuf.append(new String(slpacket.msrecord, 64, slpacket.msrecord.length - 64));
                                //} else if (type == SLPacket.TYPE_SLINFT) {
                                //    infoStrBuf.append(new String(slpacket.msrecord, 64, slpacket.msrecord.length - 64));
                                if (type == SLPacket.TYPE_SLINF) {
                                    appendInfoString(slpacket);
                                } else if (type == SLPacket.TYPE_SLINFT) {
                                    appendInfoString(slpacket);
                                    infoString = createInfoString(infoStrBuf);
                                    infoStrBuf = new StringBuffer();
                                }
                            }
                        }
                        if (state.query_mode != SLState.NO_QUERY) {
                            state.query_mode = SLState.NO_QUERY;
                        }

                    } else {   // Get packet and update the stream chain entry if not an INFO packet

                        try {
                            slpacket = state.getPacket();
                            //System.out.println("conn got DATA: > " + slpacket.getSequenceNumber());
                            updateStream(slpacket);
                            if (statefile != null) {
                                saveState(statefile);
                            }
                        } catch (SeedLinkException sle) {
                            sllog.log(true, 0, "[" + sladdr + "] bad packet: " + sle);
                            //sle.printStackTrace();
                            sendpacket = false; // the packet is broken
                        }

                    }

                    // Increment the send pointer
                    state.incrementSendPointer();
                    // After processing the packet buffer shift the data
                    state.packDataBuffer();
                    // Return packet
                    if (sendpacket) {
                        //System.out.println("conn sending:  > " + slpacket.getSequenceNumber());
                        return (slpacket);
                    }

                }

                // A trap door for terminating, all complete data packets from the buffer
                //   have been sent to the caller
                /* AJL 20040526
                if (terminateFlag) {
                return(SLPacket.SLTERMINATE);
                }
                 */
                // we are terminating (abnormally!)
                if (terminateFlag) {
                    return (doTerminate());
                }

                // AJL 20040609 moved above
                // After processing the packet buffer shift the data
                //state.packDataBuffer();
                // Catch cases where the data stream stopped
                try {
                    if (state.isError()) {
                        sllog.log(true, 0, "[" + sladdr + "] SeedLink reported an error with the last command");
                        disconnect();
                        return (SLPacket.SLERROR);
                    }
                } catch (SeedLinkException sle) {
                    ;
                } //not enough bytes to determine packet type
                try {
                    if (state.isEnd()) {
                        sllog.log(false, 1, "[" + sladdr + "] end of buffer or selected time window");
                        disconnect();
                        return (SLPacket.SLTERMINATE);
                    }
                } catch (SeedLinkException sle) {
                    ;
                } // not enough bytes to determine packet type

                // Check for more available data from the socket
                byte[] bytesread = null;
                try {
                    bytesread = receiveData(state.bytesRemaining(), sladdr);
                } catch (IOException ioe) {          // read() failed
                    sllog.log(true, 0, "[" + sladdr + "] socket read error: " + ioe + ", reconnecting in " + netdly + "s");
                    disconnect();
                    state.state = SLState.SL_DOWN;
                    state.netto_trig = -1;
                    state.netdly_trig = -1;
                }

                if (bytesread != null && bytesread.length > 0) {   // Data is here, process it
                    state.appendBytes(bytesread);
                    // Reset the timeout and keepalive timers
                    state.netto_trig = -1;
                    state.keepalive_trig = -1;
                } else {
                    Util.sleep(500);	// AJL added to prevent use of all CPU
                }
            }

            // Update timing variables when more than a 1/4 second has passed
            double current_time = Util.getCurrentTime();

            if ((current_time - state.previous_time) >= 0.25) {

                state.previous_time = current_time;

                // Network timeout timing logic
                if (netto > 0) {
                    if (state.netto_trig == -1) {  // reset timer
                        state.netto_time = current_time;
                        state.netto_trig = 0;
                    } else if (state.netto_trig == 0 && (current_time - state.netto_time) > netto) {
                        state.netto_trig = 1;
                    }
                }

                // Keepalive/heartbeat interval timing logic
                if (keepalive > 0) {
                    if (state.keepalive_trig == -1) {	// reset timer
                        state.keepalive_time = current_time;
                        state.keepalive_trig = 0;
                    } else if (state.keepalive_trig == 0 && (current_time - state.keepalive_time) > keepalive) {
                        state.keepalive_trig = 1;
                    }
                }

                // Network delay timing logic
                if (netdly > 0) {
                    if (state.netdly_trig == -1) {	// reset timer
                        state.netdly_time = current_time;
                        state.netdly_trig = 1;
                    } else if (state.netdly_trig == 1 && (current_time - state.netdly_time) > netdly) {
                        state.netdly_trig = 0;
                    }
                }

            }

        }    // End of primary loop

    }   // End of sl_collect()

    /**
     * Append the info String to the String Buffer.
     *
     * @param slpacket the SeedLink packet.
     */
    // 20170921 - Modified by Kevin Frechette <k.frechette@isti.com> to support Guralp's seedlink server
    protected void appendInfoString(SLPacket slpacket) {
        DataInputStream inStream = null;
        try {
            inStream = new DataInputStream(
                    new ByteArrayInputStream(slpacket.msrecord));
            SeedRecord record = DataRecord.read(inStream);
            if (record instanceof DataRecord) {
                String s = getString(((DataRecord) record).getData());
                infoStrBuf.append(s);
            }
        } catch (Exception ex) {
            sllog.log(true, 1,
                    "[" + sladdr + "] info packet processing failed: " + ex);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException ex) {
                }
            }
        }
    }

    /**
     * Get the integer value from the specified blockette field.
     *
     * @param blockette the blockette.
     * @param fieldNum the field number.
     * @return the integer value.
     * @throws SeedException if error.
     */
    // 20120719 - added by Kevin Frechette <k.frechette@isti.com> to support offsets other than 64 bytes (e.g. rtserve.iris.washington.edu)
    protected int getInteger(Blockette blockette, int fieldNum) throws SeedException {
        Object obj = blockette.getFieldVal(fieldNum);
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return Integer.parseInt(obj.toString());
    }

    /**
     *
     * Open a network socket connection to a SeedLink server. Expects sladdr to be in 'host:port' format.
     *
     * @exception SeedLinkException on error or no response or bad response from server.
     * @exception IOException if an I/O error occurs.
     *
     */
    public void connect() throws SeedLinkException, IOException {

        try {

            String host_name = sladdr.substring(0, sladdr.indexOf(':'));
            int nport = Integer.parseInt(sladdr.substring(sladdr.indexOf(':') + 1));

            // create and connect Socket
            //Socket sock = new Socket(host_name, nport);
            Socket sock = new Socket();

            /*try {System.out.println("sock.getReceiveBufferSize: " + sock.getReceiveBufferSize());} catch (Exception e) {System.out.println(e);}
            try {System.out.println("sock.getReuseAddress: " + sock.getReuseAddress());} catch (Exception e) {System.out.println(e);}
            try {System.out.println("sock.getKeepAlive: " + sock.getKeepAlive());} catch (Exception e) {System.out.println(e);}
             */
            sock.setReceiveBufferSize(65536);
            sock.setReuseAddress(true);
            sock.setKeepAlive(true);

            sock.connect(new InetSocketAddress(host_name, nport));

            /*try {System.out.println("sock.getReceiveBufferSize: " + sock.getReceiveBufferSize());} catch (Exception e) {System.out.println(e);}
            try {System.out.println("sock.getReuseAddress: " + sock.getReuseAddress());} catch (Exception e) {System.out.println(e);}
            try {System.out.println("sock.getKeepAlive: " + sock.getKeepAlive());} catch (Exception e) {System.out.println(e);}
             */
            // Wait up to 10 seconds for the socket to be connected
            int timeout = 10;
            int i = 0;
            while (i++ < timeout && !sock.isConnected()) {
                Util.sleep(1000);
            }
            if (!sock.isConnected()) {
                String message = "[" + sladdr + "] socket connect time-out (" + timeout + "s)";
                //sllog.log(true, 0,  message);
                throw (new SeedLinkException(message));
            }

            // socket connected
            sllog.log(false, 1, "[" + sladdr + "] network socket opened");

            // Set the KeepAlive socket option, not really useful in this case
            sock.setKeepAlive(true);

            this.socket = sock;
            this.socketInputStream = socket.getInputStream();
            this.socketOutputStream = socket.getOutputStream();

        } catch (Exception e) {
            //e.printStackTrace();
            throw (new SeedLinkException("[" + sladdr + "] cannot connect to SeedLink server: "
                    + e));
        }

        // Everything should be connected, say hello
        try {
            sayHello();
        } catch (SeedLinkException sle) {
            try {
                socket.close();
                socket = null;
            } catch (Exception e1) {
                ;
            }
            throw sle;
        } catch (IOException ioe) {
            try {
                socket.close();
                socket = null;
            } catch (Exception e1) {
                ;
            }
            throw ioe;
        }

    }	// End of connect()

    /**
     *
     * Close the network socket associated with this connection.
     *
     */
    public void disconnect() {

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ioe) {
                sllog.log(true, 1, "[" + sladdr + "] network socket close failed: " + ioe);
            }
            socket = null;
            sllog.log(false, 1, "[" + sladdr + "] network socket closed");
        }

        // make sure previous state is cleaned up
        state = new SLState();  // AJL added AJL 20040610

    }

    /* End of sl_disconnect() */
    /**
     *
     * Closes this SeedLinkConnection by closing the network socket and saving the state to the statefile, if it exists.
     *
     */
    public void close() {

        if (socket != null) {
            sllog.log(false, 1, "[" + sladdr + "] closing SeedLinkConnection()");
            disconnect();
        }
        if (statefile != null) {
            try {
                saveState(statefile);
            } catch (SeedLinkException sle) {
                sllog.log(true, 0, sle.toString());
            }
        }

    }

    /**
     *
     * Send bytes to the server. This is only designed for small pieces of data, specifically for when the server responses to commands.
     *
     * @param sendbytes bytes to send.
     * @param code a string to include in error messages for identification.
     * @param resplen if > 0 then read up to resplen response bytes after sending.
     *
     * @return the response bytes or null if no response requested.
     *
     * @exception SeedLinkException on error or no response or bad response from server.
     * @exception IOException if an I/O error occurs.
     *
     */
    public byte[] sendData(byte[] sendbytes, String code, int resplen) throws SeedLinkException, IOException {

        try {
            socketOutputStream.write(sendbytes);
        } catch (IOException ioe) {
            throw (ioe);
        }

        if (resplen <= 0) {
            return (null);   	// no response requested
        }
        // If requested, wait up to 30 seconds for a response
        byte[] bytesread = null;
        int ackcnt = 0;                     // counter for the read loop
        int ackpoll = 50;		    // poll at 0.05 seconds for reading
        int ackcntmax = 30000 / ackpoll;    // 30 second wait
        while ((bytesread = receiveData(resplen, code)) != null && bytesread.length == 0) {
            if (ackcnt > ackcntmax) {
                throw (new SeedLinkException("[" + code + "] no response from SeedLink server to '" + (new String(sendbytes)) + "'"));
            }
            Util.sleep(ackpoll);
            ackcnt++;
        }
        if (bytesread == null) {
            throw (new SeedLinkException("[" + code + "] bad response to '" + sendbytes + "'"));
        }

        return (bytesread);

    }    // End of sendData()

    /** Empty read. Callers only inspect the length, so the idle poll can share one array. */
    private static final byte[] NO_BYTES = new byte[0];

    /**
     *
     * Read bytes from the server.
     *
     * @param maxbytes maximum number of bytes to read.
     * @param code a string to include in error messages for identification.
     *
     * @return the response bytes (zero length if no available data), or null if EOF.
     *
     * @exception IOException if an I/O error occurs.
     *
     */
    public byte[] receiveData(int maxbytes, String code) throws IOException {

        // A positive max with nothing waiting used to allocate that many bytes and throw them away.
        // The following read still happens only after available() reported data, as before.
        if (maxbytes > 0 && socketInputStream.available() <= 0) {
            return NO_BYTES;
        }

        byte[] bytes = new byte[maxbytes];

        // read up to maxbytes
        int nbytesread = 0;
        try {
            if (maxbytes > 0 || socketInputStream.available() > 0) {
                nbytesread = socketInputStream.read(bytes);
            }
        } catch (IOException ioe) {
            throw (ioe);
        }

        // check for end or no bytes read
        if (nbytesread == -1) { // should indicate TCP FIN or EOF
            sllog.log(true, 1, "[" + code + "] socket.read(): " + nbytesread + ": TCP FIN or EOF received");
            return (null);
        } else if (nbytesread == 0) {
            return NO_BYTES;
        }

        // copy bytes to array of length exactly nbytesread
        byte[] bytesread = new byte[nbytesread];
        System.arraycopy(bytes, 0, bytesread, 0, nbytesread);

        return (bytesread);
    }    // End of receiveData()

    /**
     *
     * Send the HELLO command and attempt to parse the server version number from the returned string. The server version is set to 0.0 if it can not
     * be parsed from the returned string.
     *
     * @exception SeedLinkException on error.
     * @exception IOException if an I/O error occurs.
     *
     */
    public void sayHello() throws SeedLinkException, IOException {

        /* Send HELLO */
        String sendStr = "HELLO";
        sllog.log(false, 2, "[" + sladdr + "] sending: " + sendStr);
        byte[] bytes = (new String(sendStr + "\r")).getBytes();
        byte[] bytesread = null;
        bytesread = sendData(bytes, sladdr, DFT_READBUF_SIZE);

        // Parse the server ID and version from the returned string
        String servstr = null;
        try {
            servstr = new String(bytesread);
            int vndx = servstr.indexOf(" v");
            if (vndx < 0) {
                server_id = servstr;
                server_version = 0.0f;
            } else {
                server_id = servstr.substring(0, vndx);
                String tmpstr = servstr.substring(vndx + 2);
                int endndx = tmpstr.indexOf(" ");
                server_version = Float.valueOf(tmpstr.substring(0, endndx)).floatValue();
            }
        } catch (Exception e) {
            throw (new SeedLinkException("[" + sladdr + "] bad server ID/version string: '" + servstr + "'"));
        }

        // Check the response to HELLO
        if (server_id.equalsIgnoreCase("SEEDLINK")) {
            sllog.log(false, 1, "[" + sladdr + "] connected to: '" + servstr.substring(0, servstr.indexOf('\r')) + "'");
        } else {
            throw (new SeedLinkException("[" + sladdr + "] incorrect response to HELLO: '" + servstr + "'"));
        }

    }

    /* End of sl_sayhello() */
    /**
     *
     * Add an INFO request to the SeedLink Connection Description.
     *
     * @param infoLevel the INFO level (one of: ID, STATIONS, STREAMS, GAPS, CONNECTIONS, ALL)
     *
     * @exception SeedLinkException if an INFO request is already pending.
     *
     */
    public void requestInfo(String infoLevel) throws SeedLinkException {

        if (infoRequestString != null || state.expect_info) {
            throw (new SeedLinkException("[" + sladdr + "] cannot make INFO request, one is already pending"));
        } else {
            infoRequestString = infoLevel;
        }

    }                               // End of requestInfo()

    /**
     *
     * Sends a request for the specified INFO level. The verbosity level can be specified, allowing control of when the request should be logged.
     *
     * @param infoLevel the INFO level (one of: ID, STATIONS, STREAMS, GAPS, CONNECTIONS, ALL).
     *
     * @exception SeedLinkException on error.
     * @exception IOException if an I/O error occurs.
     *
     */
    public void sendInfoRequest(String infoLevel, int verb_level) throws SeedLinkException, IOException {

        if (checkVersion(2.92f) >= 0) {
            byte[] bytes = (new String("INFO " + infoLevel + "\r")).getBytes();
            sllog.log(false, verb_level, "[" + sladdr + "] sending: requesting INFO level " + infoLevel);
            sendData(bytes, sladdr, 0);
        } else {
            throw (new SeedLinkException("[" + sladdr
                    + "] detected SeedLink version (" + server_version + ") does not support INFO requests"));
        }

    }    // End of requestInfo()

    /**
     *
     * Checks server version number against a given specified value.
     *
     * @param version specified version value to test.
     *
     * @return 1 if version is greater than or equal to value specified, 0 if no server version is known, -1 if version is less than value specified.
     *
     */
    public int checkVersion(float version) {

        if (server_version == 0.0f) {
            return (0);
        } else if (server_version >= version) {
            return (1);
        } else {
            return (-1);
        }

    }    // End of checkVersion()

    /**
     *
     * Configure/negotiate data stream(s) with the remote SeedLink server. Negotiation will be either uni- or multi-station depending on the value of
     * 'multistation' in this SeedLinkConnection.
     *
     * @exception SeedLinkException on error.
     * @exception SeedLinkException if multi-station and SeedLink version does not support multi-station protocol.
     *
     */
    public void configLink() throws SeedLinkException, IOException {

        if (multistation) {
            if (checkVersion(2.5f) >= 0) {
                negotiateMultiStation();
            } else {
                throw (new SeedLinkException("[" + sladdr
                        + "] detected SeedLink version (" + server_version + ") does not support multi-station protocol"));
            }
        } else {
            negotiateUniStation();
        }

    }    // End of configLink()

    /**
     *
     * Negotiate a SeedLink connection for a single station and issue the DATA command. If selectors are defined, then the string is parsed on space
     * and each selector is sent. If 'seqnum' != -1 and the SLCD 'resume' flag is true then data is requested starting at seqnum.
     *
     * @param curstream the description of the station to negotiate.
     *
     * @exception SeedLinkException on error.
     * @exception IOException if an I/O error occurs.
     *
     */
    public void negotiateStation(SLNetStation curstream) throws SeedLinkException, IOException {

        // Send the selector(s) and check the response(s)
        String[] selectors = curstream.getSelectors();

        int acceptsel = 0;		// Count of accepted selectors
        for (int i = 0; i < selectors.length; i++) {

            String selector = selectors[i];

            if (selector.length() > SLNetStation.MAX_SELECTOR_SIZE) {
                sllog.log(false, 0, "[" + sladdr + "] invalid selector: " + selector);
            } else {

                // Build SELECT command, send it and receive response
                String sendStr = "SELECT " + selector;
                sllog.log(false, 2, "[" + sladdr + "] sending: " + sendStr);
                byte[] bytes = (new String(sendStr + "\r")).getBytes();
                byte[] bytesread = null;
                bytesread = sendData(bytes, sladdr, DFT_READBUF_SIZE);

                // Check response to SELECT
                String readStr = new String(bytesread);
                if (readStr.equals("OK\r\n")) {
                    sllog.log(false, 2, "[" + sladdr + "] response: selector " + selector + " is OK");
                    acceptsel++;
                } else if (readStr.equals("ERROR\r\n")) {
                    sllog.log(true, 0, "[" + sladdr + "] response: selector " + selector + " not accepted");
                } else {
                    throw (new SeedLinkException("[" + sladdr + "] response: invalid response to SELECT command: " + readStr));
                }
            }
        }    // End of selector processing

        // Fail if none of the given selectors were accepted
        if (acceptsel < 1) {
            throw (new SeedLinkException("[" + sladdr + "] response: no data stream selector(s) accepted"));
        } else {
            sllog.log(false, 2, "[" + sladdr + "] response: " + acceptsel + " selector(s) accepted");
        }

        // Issue the DATA, FETCH or TIME action commands.  A specified start (and
        //   optionally, stop time) takes precedence over the resumption from any
        //   previous sequence number.
        String sendStr = null;

        if (curstream.seqnum != -1 && resume) {
            // resuming

            if (dialup) {
                sendStr = "FETCH";
            } else {
                sendStr = "DATA";
            }

            // Append the last packet time if the feature is enabled and server is >= 2.93
            if (lastpkttime && checkVersion(2.93f) >= 0 && curstream.btime != null) {
                // Increment sequence number by 1
                sendStr += " " + Integer.toHexString(curstream.seqnum + 1) + " " + curstream.getSLTimeStamp();
                sllog.log(false, 1, "[" + sladdr + "] requesting resume data from 0x"
                        + Integer.toHexString(curstream.seqnum + 1).toUpperCase() + " (decimal: " + (curstream.seqnum + 1)
                        + ") at " + curstream.getSLTimeStamp());
            } else {
                // Increment sequence number by 1
                sendStr += " " + Integer.toHexString(curstream.seqnum + 1);
                sllog.log(false, 1, "[" + sladdr + "] requesting resume data from  0x"
                        + Integer.toHexString(curstream.seqnum + 1).toUpperCase() + " (decimal: " + (curstream.seqnum + 1) + ")");
            }

        } else if (begin_time != null) {
            // begin time specified (should only be at initial startup)

            if (checkVersion(2.92f) >= 0) {
                if (end_time == null) {
                    sendStr = "TIME " + begin_time;
                } else {
                    sendStr = "TIME " + begin_time + " " + end_time;
                }
                sllog.log(false, 1, "[" + sladdr + "] requesting specified time window");
            } else {
                throw (new SeedLinkException("[" + sladdr
                        + "] detected SeedLink version (" + server_version + ") does not support TIME windows"));
            }

        } else {
            // default

            if (dialup) {
                sendStr = "FETCH";
            } else {
                sendStr = "DATA";
            }
            sllog.log(false, 1, "[" + sladdr + "] requesting next available data");
        }

        // Send action command and receive response
        sllog.log(false, 2, "[" + sladdr + "] sending: " + sendStr);
        byte[] bytes = (new String(sendStr + "\r")).getBytes();
        byte[] bytesread = null;
        bytesread = sendData(bytes, sladdr, DFT_READBUF_SIZE);

        // Check response to DATA/FETCH/TIME
        String readStr = new String(bytesread);
        if (readStr.equals("OK\r\n")) {
            sllog.log(false, 2, "[" + sladdr + "] response: DATA/FETCH/TIME command is OK");
            acceptsel++;
        } else if (readStr.equals("ERROR\r\n")) {
            throw (new SeedLinkException("[" + sladdr + "] response: DATA/FETCH/TIME command is not accepted"));
        } else {
            throw (new SeedLinkException("[" + sladdr + "] response: invalid response to DATA/FETCH/TIME command: " + readStr));
        }

    }    // End of negotiateStation()

    /**
     *
     * Negotiate a SeedLink connection in uni-station mode and issue the DATA command. This is compatible with SeedLink Protocol version 2 or greater.
     * If selectors are defined, then the string is parsed on space and each selector is sent. If 'seqnum' != -1 and the SLCD 'resume' flag is true
     * then data is requested starting at seqnum.
     *
     * @exception SeedLinkException on error.
     * @exception IOException if an I/O error occurs.
     *
     */
    public void negotiateUniStation() throws SeedLinkException, IOException {

        // get stream (should be only stream present)
        SLNetStation curstream = null;
        try {
            curstream = (SLNetStation) streams.elementAt(0);
        } catch (Exception e) {
            throw (new SeedLinkException("[" + sladdr
                    + "] cannot negotiate uni-station, stream list does not have exactly one element"));
        }
        if (!(curstream.net.equals(UNINETWORK) && curstream.station.equals(UNISTATION))) {
            throw (new SeedLinkException("[" + sladdr + "] cannot negotiate uni-station, mode not configured!"));
        }

        // negotiate the station connection
        negotiateStation(curstream);

    }    // End of negotiateUniStation()

    /**
     *
     * Negotiate a SeedLink connection using multi-station mode and issue the END action command. This is compatible with SeedLink Protocol version 3,
     * multi-station mode. If selectors are defined, then the string is parsed on space and each selector is sent. If 'seqnum' != -1 and the SLCD
     * 'resume' flag is true then data is requested starting at seqnum.
     *
     * @exception SeedLinkException on error.
     * @exception IOException if an I/O error occurs.
     *
     */
    public void negotiateMultiStation() throws SeedLinkException, IOException {

        int acceptsta = 0;
        /* Count of accepted stations */

        if (streams.size() < 1) {
            throw (new SeedLinkException("[" + sladdr
                    + "] cannot negotiate multi-station, stream list is empty"));
        }

        // Loop through the stream chain
        for (int i = 0; i < streams.size(); i++) {
            // get stream (should be only stream present)
            SLNetStation curstream = (SLNetStation) streams.elementAt(i);

            // A ring identifier
            String slring = curstream.net + curstream.station;

            // Build STATION command, send it and receive response
            String sendStr = "STATION  " + curstream.station + " " + curstream.net;
            sllog.log(false, 2, "[" + sladdr + "] sending: " + sendStr);
            byte[] bytes = (new String(sendStr + "\r")).getBytes();
            byte[] bytesread = null;
            bytesread = sendData(bytes, sladdr, DFT_READBUF_SIZE);

            // Check response to SELECT
            String readStr = new String(bytesread);
            if (readStr.equals("OK\r\n")) {
                sllog.log(false, 2, "[" + sladdr + "] response: station is OK (selected)");
            } else if (readStr.equals("ERROR\r\n")) {
                sllog.log(true, 0, "[" + sladdr + "] response: station not accepted, skipping");
                continue;
            } else {
                throw (new SeedLinkException("[" + sladdr + "] response: invalid response to STATION command: " + readStr));
            }

            // negotiate the station connection
            try {
                negotiateStation(curstream);
            } catch (Exception e) {
                sllog.log(true, 0, e.toString());
                continue;
            }

            acceptsta++;

        }	// End of stream and selector config (end of stream chain)

        // Fail if no stations were accepted
        if (acceptsta < 1) {
            throw (new SeedLinkException("[" + sladdr + "] no stations accepted"));
        } else {
            sllog.log(false, 1, "[" + sladdr + "] " + acceptsta + " station(s) accepted");
        }

        // Issue END action command
        String sendStr = "END";
        sllog.log(false, 2, "[" + sladdr + "] sending: " + sendStr);
        byte[] bytes = (new String(sendStr + "\r")).getBytes();
        sendData(bytes, sladdr, 0);

    }    // End of configlink_multi()

    /**
     *
     * Update the appropriate stream chain entry given a Mini-SEED record.
     *
     * @param slpacket the packet containing a Mini-SEED record.
     *
     * @exception SeedLinkException on error.
     *
     */
    public void updateStream(SLPacket slpacket) throws SeedLinkException {

        int seqnum = slpacket.getSequenceNumber();
        if (seqnum == -1) {
            throw (new SeedLinkException("[" + sladdr + "] could not determine sequence number"));
        }

        Blockette blockette = slpacket.getBlockette();

        if (blockette.getType() != 999) {
            throw (new SeedLinkException("[" + sladdr + "] blockette not 999 (Fixed Section Data Header)"));
        }

        // read some blockette fields
        String net = null;
        String station = null;
        Btime btime = null;
        try {
            station = (String) blockette.getFieldVal(4);
            net = (String) blockette.getFieldVal(7);
            btime = (Btime) blockette.getFieldVal(8);
        } catch (SeedException se) {
            throw (new SeedLinkException("[" + sladdr + "] blockette read error: " + se));
        }

        // For uni-station mode
        if (!multistation) {
            // get stream (should be only stream present)
            SLNetStation curstream = null;
            try {
                curstream = (SLNetStation) streams.elementAt(0);
            } catch (Exception e) {
                throw (new SeedLinkException("[" + sladdr
                        + "] cannot update uni-station stream, stream list does not have exactly one element"));
            }
            curstream.seqnum = seqnum;
            curstream.btime = btime;
            return;
        }

        // For multi-station mode, search the stream chain
        // Search for a matching net/station in the stream chain
        // AJL 20090306 - Add support for IRIS DMC enhancements:
        // Enhancements to the SeedLink protocol supported by the DMC's server allow network and station codes to be
        // wildcarded in addition to the location and channel codes.
        boolean wildcarded = false;
        SLNetStation stream = null;
        for (int i = 0; i < streams.size(); i++) {
            stream = (SLNetStation) streams.elementAt(i);
            if (stream.net.equals(net) && stream.station.equals(station)) {
                break;	// found
            }
            if (stream.net.contains("?") || stream.net.contains("*") || stream.station.contains("?") || stream.station.contains("*")) {
                wildcarded = true;
            }	// wildcard character found
            stream = null;
        }
        // update net/station entry in the stream chain
        if (stream != null) {
            stream.seqnum = seqnum;
            stream.btime = btime;
        } else {
            // If we got here no match was found
            if (!wildcarded) {
                sllog.log(true, 0, "unexpected data received: " + net + " " + station);
            }
        }

    }    // End of updateStream()
}
