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
 * SLNetStation.java
 *
 * Created on 05 April 2004, 15:49
 */
package nl.knmi.orfeus.seedlink.client;

/**
 *
 * @author  Anthony Lomax
 */
import nl.knmi.orfeus.seedlink.*;

import edu.iris.Fissures.seed.container.Btime;
import edu.iris.Fissures.seed.exception.*;

import java.util.StringTokenizer;

/**
 *
 * Class to hold a SeedLink stream descriptions (selectors) for a network/station.
 *
 * @see edu.iris.Fissures.seed.container.Blockette
 *
 */
public class SLNetStation {

    /** Maximum selector size. */
    public static int MAX_SELECTOR_SIZE = 8;
    /** The network code. */
    public String net = null;
    /** The station code. */
    public String station = null;
    /** SeedLink style selectors for this station. */
    public String selectors = null;
    /** SeedLink sequence number of last packet received. */
    public int seqnum = -1;
    /** Time stamp of last packet received. */
    public Btime btime = null;

    /**
     * Creates a new instance of SLNetStation.
     *
     * @param net network code.
     * @param station station code.
     * @param selectors selectors for this net/station, null if none.
     * @param seqnum SeedLink sequence number of last packet received, -1 to start at the next data.
     * @param timestamp SeedLink time stamp in SEED "year,day-of-year,hour,minute,second" format for last packet received, null for none.
     *
     */
    public SLNetStation(String net, String station, String selectors, int seqnum, String timestamp) throws SeedLinkException {

        this.net = new String(net);
        this.station = new String(station);
        if (selectors != null) {
            this.selectors = new String(selectors);
        }
        this.seqnum = seqnum;
        if (timestamp != null) {
            try {
                this.btime = new Btime(timestamp);
            } catch (SeedInputException sie) {
                throw (new SeedLinkException("failed to parse timestamp: " + sie));
            }
        }

    }

    /**
     *
     * Appends a selectors String to the current selectors for this SLNetStation
     *
     * @return 0 if selectors added sucessfully, 1 otherwise
     *
     */
    public int appendSelectors(String newSelectors) {

        selectors += " " + newSelectors;

        return (1);

    }

    /**
     *
     * Returns the selectors as an array of Strings
     *
     * @return array of selector Strings
     *
     */
    public String[] getSelectors() {

        try {
            StringTokenizer selTkz = new StringTokenizer(selectors);
            String[] selStrings = new String[selTkz.countTokens()];
            for (int i = 0; i < selStrings.length; i++) {
                selStrings[i] = selTkz.nextToken();
            }
            return (selStrings);
        } catch (Exception e) {
            return (new String[0]);
        }


    }

    /**
     *
     * Returns the time stamp in SeedLink string format: "year,month,day,hour,minute,second"
     *
     * @return SeedLink time
     *
     */
    public String getSLTimeStamp() {

        StringBuffer strbuf = new StringBuffer();
        strbuf.append(Btime.getMonthDay(btime.getYear(), btime.getDayOfYear()));
        strbuf.append(',').append(btime.getHour());
        strbuf.append(',').append(btime.getMinute());
        strbuf.append(',').append(btime.getSecond());

        String slTimeStr = strbuf.toString();
        slTimeStr = slTimeStr.replace('/', ',');
        slTimeStr = slTimeStr.replace(':', ',');

        return (slTimeStr);


    }
}
