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
 * SLPacket.java
 *
 * Created on 06 April 2004, 11:00
 */
package nl.knmi.orfeus.seedlink;

/**
 *
 * @author Anthony Lomax
 */
import edu.iris.Fissures.seed.builder.*;
import edu.iris.Fissures.seed.container.*;
import edu.iris.Fissures.seed.director.*;

import java.io.*;

/**
 *
 * Class to hold and decode a SeedLink packet.
 *
 * @see edu.iris.Fissures.seed.container.Blockette
 *
 */
public class SLPacket {

    /**
     * Packet type is terminated info packet.
     */
    public static final int TYPE_SLINFT = -101;
    /**
     * Packet type is non-terminated info packet.
     */
    public static final int TYPE_SLINF = -102;
    /**
     * Terminate flag - connection was closed by the server or the termination sequence completed.
     */
    public static final SLPacket SLTERMINATE = new SLPacket();
    /**
     * No packet flag - indicates no data availbale.
     */
    public static final SLPacket SLNOPACKET = new SLPacket();
    /**
     * Error flag - indicates server reported an error.
     */
    public static final SLPacket SLERROR = new SLPacket();
    /**
     * SeedLink packet header size.
     */
    public static final int SLHEADSIZE = 8;
    /**
     * Mini-SEED record size.
     */
    public static final int SLRECSIZE = 512;
    /**
     * SeedLink header signature.
     */
    public static String SIGNATURE = "SL";
    /**
     * SeedLink INFO packet signature.
     */
    public static String INFOSIGNATURE = "SLINFO";
    /**
     * SeedLink ERROR signature.
     */
    public static String ERRORSIGNATURE = "ERROR\r\n";
    /**
     * SeedLink END signature.
     */
    public static String ENDSIGNATURE = "END";
    /**
     * The SeedLink header
     */
    public byte[] slhead = null;
    /**
     * The mini-SEED record
     */
    public byte[] msrecord = null;
    /**
     * The Blockette conained in msrecord.
     */
    protected Blockette blockette = null;

    /**
     * Empty constructor used for internal constants
     */
    protected SLPacket() {
    }

    /**
     * Creates a new instance of SLPacket by converting the specified subarray of bytes.
     *
     * @param bytes The bytes to be converted.
     * @param offset Index of the first byte to convert.
     *
     * @exception SeedLinkException if there are not enough bytes in the subarray
     */
    public SLPacket(byte bytes[], int offset) throws SeedLinkException {

        if (bytes.length - offset < SLHEADSIZE + SLRECSIZE) {
            throw (new SeedLinkException("not enough bytes in subarray to construct a new SLPacket"));
        }

        // SeedLink header
        slhead = new byte[SLHEADSIZE];
        System.arraycopy(bytes, offset, slhead, 0, SLHEADSIZE);
        // mini-SEED record
        msrecord = new byte[SLRECSIZE];
        System.arraycopy(bytes, offset + SLHEADSIZE, msrecord, 0, SLRECSIZE);

    }

    /**
     *
     * Check for 'SL' signature and get sequence number.
     *
     * @return the packet sequence number of this SeedLink packet on success, 0 for INFO packets or -1 on error.
     *
     */
    public int getSequenceNumber() {

        if (slhead == null || slhead.length < SLHEADSIZE || !ASCII_COMPATIBLE
                || INFOSIGNATURE == null || SIGNATURE == null
                || INFOSIGNATURE.length() > slhead.length || SIGNATURE.length() > slhead.length
                || !asciiBytes(slhead, 0, SLHEADSIZE)) {
            return sequenceNumberLegacy();
        }

        if (equalsIgnoreCaseAscii(slhead, 0, INFOSIGNATURE)) {
            return 0;
        }

        if (!equalsIgnoreCaseAscii(slhead, 0, SIGNATURE)) {
            return -1;
        }

        try {
            return parseAsciiInt(slhead, 2, 6, 16);
        } catch (NumberFormatException nfe) {
            String seqstr = new String(slhead, 2, 6);
            System.out.println("SLPacket.getSequenceNumber(): bad packet sequence number: " + seqstr);
            return -1;
        }
    }

    /**
     * Original header decode, used when the bytes are not plain ASCII.
     */
    private int sequenceNumberLegacy() {

        if ((new String(slhead)).substring(0, INFOSIGNATURE.length()).equalsIgnoreCase(INFOSIGNATURE)) {
            return 0;
        }

        if (!(new String(slhead)).substring(0, SIGNATURE.length()).equalsIgnoreCase(SIGNATURE)) {
            return -1;
        }

        String seqstr = new String(slhead, 2, 6);

        int seqnum = -1;
        try {
            seqnum = Integer.parseInt(seqstr, 16);
        } catch (NumberFormatException nfe) {
            System.out.println("SLPacket.getSequenceNumber(): bad packet sequence number: " + seqstr);
            return -1;
        }

        return (seqnum);
    }

    /**
     * Determines the type of packet. First check for an INFO packet, if not, assume packet contains single blockette and return its type.
     *
     * @return the packet type.
     */
    public int getType() throws SeedLinkException {

        if (slhead == null || !ASCII_COMPATIBLE || INFOSIGNATURE == null
                || slhead.length < INFOSIGNATURE.length()
                || !asciiBytes(slhead, 0, INFOSIGNATURE.length())) {
            return typeLegacy();
        }

        // Check for an INFO packet
        if (equalsIgnoreCaseAscii(slhead, 0, INFOSIGNATURE)) {
            // Check if it is terminated
            if (slhead[SLHEADSIZE - 1] != '*') {
                return (TYPE_SLINFT);
            } else {
                return (TYPE_SLINF);
            }
        }

        // assume packet contains single blockette and return its type
        // NOTE: in libslink, the type of the first "important" blockette is returned
        return (getBlockette().getType());

    }				// End of sl_packettype()

    /**
     * Original packet-type decode, used when the header is not plain ASCII.
     */
    private int typeLegacy() throws SeedLinkException {

        // Check for an INFO packet
        if ((new String(slhead)).substring(0, SLPacket.INFOSIGNATURE.length()).equalsIgnoreCase(SLPacket.INFOSIGNATURE)) {
            // Check if it is terminated
            if (slhead[SLHEADSIZE - 1] != '*') {
                return (TYPE_SLINFT);
            } else {
                return (TYPE_SLINF);
            }
        }

        // assume packet contains single blockette and return its type
        // NOTE: in libslink, the type of the first "important" blockette is returned
        return (getBlockette().getType());

    }

    /**
     *
     * Returns the Blockette contained in this SLPacket. Creates the blockette if necessary.
     *
     * @return the blockette contained in this SeedLink packet.
     *
     * @exception SeedLinkException on error.
     *
     * @see edu.iris.Fissures.seed.container.Blockette
     * @see edu.iris.Fissures.seed.builder.SeedObjectBuilder
     * @see edu.iris.Fissures.seed.director.SeedImportDirector
     *
     */
    public Blockette getBlockette() throws SeedLinkException {

        // blockette already created
        if (this.blockette != null) {
            return (blockette);
        }


        //  create a Builder
        SeedObjectBuilder seedBuilder = new SeedObjectBuilder();
        // create the Director
        SeedImportDirector seedDirector = new SeedImportDirector(seedBuilder);

        try {

            // parse the record
            DataInputStream seedIn = new DataInputStream(new ByteArrayInputStream(msrecord));
            seedDirector.construct(seedIn);

            // extract first blockette
            SeedObjectContainer container = (SeedObjectContainer) seedBuilder.getContainer();
            int numElem = container.iterate(); // all Blockettes
            //System.out.println("num elements in packet = " + numElem);
            this.blockette = (Blockette) container.getNext();

            // reset the volume counter
            // 20130207 - Bug fix by Kevin Frechette <k.frechette@isti.com>
            // uncomment following line if you are using a later version of edu.iris.Fissures
            //edu.iris.Fissures.seed.container.BlocketteDecoratorFactory.reset();
            /*
             * I found a problem using Java SeedLink with the current version of JavaSeed (3.8) and I wanted to share with you how to fix it
             * in case you want to update the version you are using at some point.
             * The problem is JavaSeed now automatically increments the volume number every time you import data and it throws an Exception
             * when the volume number is greater than 214 (edu.iris.Fissures.seed.container.SeedObjectContainer#getNewId).
             */

            return (blockette);

        } catch (Exception e) {
            //e.printStackTrace();
            throw (new SeedLinkException("failed to decode mini-seed record: " + e));
        }

    }

    /**
     * True when the platform charset decodes every byte below 128 as that same character.
     * SeedLink headers are ASCII, so the common path can compare bytes instead of building strings.
     */
    private static final boolean ASCII_COMPATIBLE = asciiCompatibleCharset();

    private static boolean asciiCompatibleCharset() {
        byte[] bytes = new byte[128];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        String decoded = new String(bytes);
        if (decoded.length() != bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (decoded.charAt(i) != i) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiBytes(byte[] buf, int offset, int length) {
        for (int i = 0; i < length; i++) {
            if ((buf[offset + i] & 0xff) > 0x7f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Same result as {@code new String(buf, offset, signature.length()).equalsIgnoreCase(signature)}
     * when those bytes are ASCII and the charset keeps them unchanged.
     */
    private static boolean equalsIgnoreCaseAscii(byte[] buf, int offset, String signature) {
        int length = signature.length();
        for (int i = 0; i < length; i++) {
            char left = (char) (buf[offset + i] & 0xff);
            char right = signature.charAt(i);
            if (left == right) {
                continue;
            }
            char upperLeft = Character.toUpperCase(left);
            char upperRight = Character.toUpperCase(right);
            if (upperLeft == upperRight) {
                continue;
            }
            if (Character.toLowerCase(upperLeft) == Character.toLowerCase(upperRight)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Same value as {@code Integer.parseInt} for an ASCII byte region.
     */
    private static int parseAsciiInt(byte[] buf, int offset, int length, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new NumberFormatException();
        }
        int result = 0;
        boolean negative = false;
        int i = 0;
        int limit = -Integer.MAX_VALUE;
        if (length > 0) {
            char first = (char) (buf[offset] & 0xff);
            if (first < '0') {
                if (first == '-') {
                    negative = true;
                    limit = Integer.MIN_VALUE;
                } else if (first != '+') {
                    throw new NumberFormatException();
                }
                if (length == 1) {
                    throw new NumberFormatException();
                }
                i++;
            }
            int multmin = limit / radix;
            while (i < length) {
                int digit = Character.digit((char) (buf[offset + i] & 0xff), radix);
                i++;
                if (digit < 0) {
                    throw new NumberFormatException();
                }
                if (result < multmin) {
                    throw new NumberFormatException();
                }
                result *= radix;
                if (result < limit + digit) {
                    throw new NumberFormatException();
                }
                result -= digit;
            }
        } else {
            throw new NumberFormatException();
        }
        return negative ? result : -result;
    }
}
