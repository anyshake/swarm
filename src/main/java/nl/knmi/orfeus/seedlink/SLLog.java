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
 * SLLog.java
 *
 * Created on 05 April 2004, 10:48
 */

package nl.knmi.orfeus.seedlink;

/**
 *
 * @author  Anthony Lomax
 */


import java.io.*;


/**
 *
 * Class to manage the logging of information and error messages.
 *
 */

public class SLLog {

    /** The stream used for output of information messages */
    protected PrintStream log = System.out;

    /** The prefix to prepend to information messages */
    protected String logPrefix = "";

    /** The stream used for output of error messages */
    protected PrintStream err = System.out;
    //protected PrintStream err = System.err;

    /** The prefix to prepend to error messages */
    protected String errPrefix = "ERROR: ";

    /** Verbosity level, 0 is lowest. */
    protected int  verbosity = 0;


    /**
     *
     * Creates a new default instance of SLLog.
     *
     */

    public SLLog() {
    }


    /**
     *
     * Creates a new instance of SLLog with the specified parameters.
     *
     * @param verbosity verbosity level, 0 is lowest.
     * @param log stream used for output of informartion messages.
     * @param logPrefix prefix to prepend to informartion messages.
     * @param err stream used for output of error messages.
     * @param errPrefix prefix to prepend to error messages.
     *
     */

    public SLLog(int verbosity, PrintStream log, String logPrefix, PrintStream err, String errPrefix) {

	this.verbosity = verbosity;
	if (log != null)
	    this.log = log;
	if (logPrefix != null)
	    this.logPrefix = logPrefix;
	if (err != null)
	    this.err = err;
	if (errPrefix != null)
	    this.errPrefix = errPrefix;

    }


    /** print the message in appropriate manner */

    /**
     *
     * Logs a message in appropriate manner.
     *
     * @param isError true if error message, false otherwise.
     * @param verbosity verbosity level for this messages.
     * @param message message text.
     *
     */

    public void log(boolean isError, int verbosity, String message) {

	if (verbosity > this.verbosity)
	    return;

	// error message
	if (isError)
	    err.println(errPrefix + message);
	else
	    log.println(logPrefix + message);


    }

}
