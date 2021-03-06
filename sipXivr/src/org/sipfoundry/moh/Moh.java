/*
 *
 *
 * Copyright (C) 2008-2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */

package org.sipfoundry.moh;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.sipfoundry.commons.freeswitch.PromptList;
import org.sipfoundry.commons.userdb.User;
import org.sipfoundry.commons.userdb.ValidUsers;
import org.sipfoundry.sipxivr.SipxIvrApp;

public class Moh extends SipxIvrApp {
    static final Logger LOG = Logger.getLogger("org.sipfoundry.sipxivr");

    private String m_dataDirectory;
    private String m_promptsDir;
    private ValidUsers m_validUsers;

    enum NextAction {
        repeat, exit, nextAttendant;
    }

    /**
     * Run each Moh until there is nothing left to do. If the SIP URL didn't pass in a particular
     * attendant name, use the current time of day and the schedule to find which attendant to
     * run.
     * 
     * Keep running the next returned attendant until there are none left, then exit.
     * 
     * @throws Throwable indicating an error or hangup condition.
     */
    @Override
    public void run() {
        MohEslRequestController controller = (MohEslRequestController) getEslRequestController();
        String id = StringUtils.defaultIfEmpty(controller.getMohParam(), StringUtils.EMPTY);
        LOG.info(String.format("Moh::run Moh %s determined from URL parameter", id));

        // Wait it bit so audio doesn't start too fast
        controller.sleep(1000);
        moh(id, controller);
    }

    /**
     * Do the specified Moh.
     * 
     * @param id The id of the moh. <br>
     * 
     *        <pre>
     * id       meaning
     * -------  ----------
     * (empty)  use original park server music (/var/sipxdata/parkserver/music/)
     * l        use local_stream://moh (defined in local_stream.conf.xml)
     * p        use portaudio_stream:// (defined in portaudio.conf.xml)
     * u{user}  use per user music ({data}/moh/{username})
     * n        music source "None", so just hangup.  Silence might also work.
     * </pre>
     */
    void moh(String id, MohEslRequestController controller) {
        LOG.info("Moh::moh Starting moh id (" + id + ") in locale " + controller.getLocale());

        PromptList pl = controller.getPromptList();
        String musicPath;
        if (id.equals("l")) {
            musicPath = "local_stream://moh";
        } else if (id.equals("p")) {
            musicPath = "portaudio_stream://";
        } else if (id.startsWith("u")) {
            String userName = id.substring(1);
            User user = m_validUsers.getUser(userName);
            if (user != null) {
                musicPath = m_dataDirectory + "/moh/" + user.getUserName();
            } else {
                // Use default FreeSWITCH MOH
                musicPath = "local_stream://moh";
            }
        } else if (id.equals("n")) {
            // "n" means "Music Source None". Easiest thing to do is just hangup.
            return;
        } else {
            // Use original park server music
            musicPath = m_promptsDir + "/../../../parkserver/music/";
        }
        if (musicPath.contains("://")) {
            LOG.info("Moh::moh Using MOH URL " + musicPath);
            // musicPath is a URL (that FreeSWITCH knows how to deal with)
            pl.addUrl(musicPath);
        } else {
            // musicPath is a file or directory
            File musicPathFile = new File(musicPath);
            if (musicPathFile.isFile()) {
                LOG.info("Moh::moh Using MOH File " + musicPath);
                // musicPath is a file Use it.
                pl.addPrompts(musicPath);
            } else if (musicPathFile.isDirectory()) {
                LOG.info("Moh::moh Using MOH Directory " + musicPath);
                // musicPath is a directory. Find all the files inside,
                // sort alphabetically, and use them.
                File[] musicFiles = musicPathFile.listFiles();
                Arrays.sort(musicFiles);
                for (File musicFile : musicFiles) {
                    pl.addPrompts(musicFile.getPath());
                }
            } else {
                LOG.warn("Moh::moh MOH path unknown " + musicPath);
                // Oops. Something not found, use default FS MOH
                pl.addUrl("local_stream://moh");
            }
        }
        // Play the music until someone hangs up.
        // (FreeSWITCH will hang up after 300 seconds with no RTP)
        for (;;) {
            controller.play(pl, "");
            controller.sleep(1000);
        }
    }

    public void setDataDirectory(String dir) {
        m_dataDirectory = dir;
    }

    public void setPromptsDir(String dir) {
        m_promptsDir = dir;
    }

    public void setValidUsers(ValidUsers validUsers) {
        m_validUsers = validUsers;
    }

}
