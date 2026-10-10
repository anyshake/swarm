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
 * Util.java
 *
 * Created on 06 April 2004, 14:28
 */

package nl.knmi.orfeus.seedlink;

/**
 *
 * @author  Anthony Lomax
 */

import java.util.Date;


/**
 *
 * Class of SeedLink utility methods.
 *
 */

public class Util {
    
    /** Util cannot be instantiated. */
    
    private Util() {
    }
    
    
    /**
     *
     * Returns the current system time.
     *
     * @return the number of seconds since January 1, 1970, 00:00:00 GMT represented by the current system time.
     *
     */
    public static double getCurrentTime() {
	
	return(((double) (new Date()).getTime()) / 1000.0);
	
    }
    
    
    /**
     *
     * Causes the currently executing thread to sleep (temporarily cease execution) for the specified number of milliseconds.
     *
     * @param millis sleep time in milliseconds.
     *
     */
    public static void sleep(long millis) {
	try {
	    Thread.currentThread().sleep(millis);
	} catch (Exception e) {;}
    }
    
}
