package tppbot;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

/**
 *
 * @author Michael
 * @since September 8, 2014 @ 10:39pm
 */
public class TPPStatsBotMain {

    private static String name = "";
    private static String oAuth = "";
    private static File mainConfig = new File("config.cfg");
    private static boolean flashWindow;

    /**
     * Main program method
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        flashWindow = false;
        if (!mainConfig.exists()) {
            JOptionPane.showMessageDialog(null, "Welcome to the TPP Stats Bot! Before the bot\ncan start, it needs some information from you.", "Information", JOptionPane.PLAIN_MESSAGE);
            JDialog d = new JDialog();
            d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            d.setModal(true);
            d.setTitle("First Run Setup");
            d.setResizable(false);
            d.setSize(500, 225);
            d.setLayout(new BorderLayout());
            JPanel main = new JPanel();
            main.add(new JLabel("Enter your Twitch username:"));
            JTextField username = new JTextField(25);
            main.add(username);
            main.add(new JLabel("Enter this URL into your browser:"));
            JTextField url = new JTextField("http://www.twitchapps.com/tmi/ ");
            url.setEditable(false);
            main.add(url);
            JLabel instructions = new JLabel("<html>Connect your Twitch Account and copy-paste<br>the ENTIRE oauth password below (INCLUDING THE oauth: PREFIX!!)</html>");
            instructions.setHorizontalAlignment(JLabel.CENTER);
            main.add(instructions);
            JTextField password = new JTextField(35);
            main.add(password);
            d.add(main, BorderLayout.CENTER);
            JPanel south = new JPanel();
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Close");
            ok.addActionListener((ActionEvent e) -> {
                name = username.getText();
                oAuth = password.getText();
                try {
                    mainConfig.createNewFile();
                    try (PrintWriter pw = new PrintWriter(mainConfig)) {
                        pw.println("[StatsBot]");
                        pw.println("name=" + name);
                        pw.println("oauth=" + oAuth);
                        pw.println("flashonmessage=1");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Failed to write the new config file!\nCheck to see if you have permission to write in the Stats Bot directory.", "Fatal Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(7);
                }
                d.dispose();
                d.setVisible(false);
            });
            cancel.addActionListener((ActionEvent e) -> {
                System.exit(0);
            });
            south.add(ok);
            south.add(cancel);
            d.add(south, BorderLayout.SOUTH);
            d.setVisible(true);
        }
        try {
            Scanner cfgReader = new Scanner(mainConfig);
            if (cfgReader.nextLine().startsWith("[StatsBot]")) {
                while (cfgReader.hasNextLine()) {
                    String line = cfgReader.nextLine();
                    if (line.startsWith("name=")) {
                        name = line.split("name=")[1];
                    }
                    if (line.startsWith("oauth=")) {
                        oAuth = line.split("oauth=")[1];
                    }
                    if (line.startsWith("flashonmessage=")) {
                        String flash = line.split("flashonmessage=")[1];
                        flashWindow = !flash.equals("0");
                    }
                }
            } else {
                JOptionPane.showMessageDialog(null, "Main Config File is corrupted!\nPlease reinstall Stats Bot.", "Fatal Error", JOptionPane.ERROR_MESSAGE);
                System.exit(3);
            }
        } catch (FileNotFoundException ex) {
            JOptionPane.showMessageDialog(null, "Main Config File not found or corrupt!\nPlease reinstall Stats Bot.", "Fatal Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } catch (ArrayIndexOutOfBoundsException ex) {
            JOptionPane.showMessageDialog(null, "Main Config File is corrupted!\nPlease reinstall Stats Bot.", "Fatal Error", JOptionPane.ERROR_MESSAGE);
            System.exit(2);
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        name = capitalize(name.toLowerCase());
        boolean debug = false;
        if (args.length > 0) {
            for (String el : args) {
                if (el.equals("-debug")) {
                    debug = true;
                }
            }
        }
        TPPStatsBot b = new TPPStatsBot(name, debug);
        b.g.setWindowFlash(flashWindow);
        //b.setVerbose(true);
        try {
            b.connect("irc.twitch.tv", 6667, oAuth);
            b.joinChannel("#twitchplayspokemon");
            Thread t = new Thread(() -> {
                long timeDiff = System.currentTimeMillis() - b.LAST_MESSAGE_TIME;
                if (timeDiff > 600000 && b.STATE != State.UNKNOWN) {
                    try {
                        b.reconnect();
                        b.joinChannel("#twitchplayspokemon");
                        b.STATE = State.UNKNOWN;
                        System.out.println("[RECONNECT] " + getDateTime() + " Reconnected to IRC.");
                        b.appendLog("[RECONNECT] Reconnected to IRC.");
                    } catch (Exception ex) {
                        System.out.println("[WARNING] " + getDateTime() + " Failed to reconnect to IRC!");
                        ex.printStackTrace();
                        b.appendLog("[WARNING] Failed to reconnect to IRC!" + "\n" + TPPStatsBot.getStackTrace(ex));
                    }
                }
            });
            executor.scheduleAtFixedRate(t, 1, 60, TimeUnit.SECONDS);
            Thread update = new Thread(() -> {
                if (b.STATE == State.UNKNOWN || b.STATE == State.PRE_BATTLE || b.STATE == State.TEN_SEC_LEFT) {
                    b.g.updateList();
                }
            });
            executor.scheduleAtFixedRate(update, 1, 2500, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
        }
    }

    /**
     * Gets the current date and time in the computers timezone
     *
     * @return Date and Time in String format
     */
    public static final String getDateTime() {
        Calendar d = Calendar.getInstance();
        int minInt = d.get(Calendar.MINUTE);
        int secInt = d.get(Calendar.SECOND);
        int hourInt = d.get(Calendar.HOUR_OF_DAY);
        int monthInt = d.get(Calendar.MONTH) + 1;
        String month = String.valueOf(monthInt);
        if (month.length() < 2) {
            month = "0" + month;
        }
        String day = String.valueOf(d.get(Calendar.DATE));
        if (day.length() < 2) {
            day = "0" + day;
        }
        String min = "";
        String sec = "";
        String hour = "";
        if (hourInt < 10) {
            hour = "0" + hourInt;
        } else {
            hour = String.valueOf(hourInt);
        }
        if (minInt < 10) {
            min = "0" + minInt;
        } else {
            min = String.valueOf(minInt);
        }
        if (secInt < 10) {
            sec = "0" + secInt;
        } else {
            sec = String.valueOf(secInt);
        }
        return (d.get(Calendar.YEAR) + "-" + month + "-" + day + " " + hour + ":" + min + ":" + sec);
    }

    /**
     * Gets the date and time from a certain unix time
     *
     * @param unixTime Unix Time to convert
     * @return Date and Time converted from Unix Time
     */
    public static final String getDateTime(long unixTime) {
        String buffer = String.valueOf(unixTime);
        buffer = buffer.substring(0, buffer.length() - 3);
        unixTime = Long.parseLong(buffer);
        Calendar d = Calendar.getInstance();
        d.setTime(new Date(unixTime * 1000));
        int minInt = d.get(Calendar.MINUTE);
        int secInt = d.get(Calendar.SECOND);
        int hourInt = d.get(Calendar.HOUR_OF_DAY);
        int monthInt = d.get(Calendar.MONTH) + 1;
        String month = String.valueOf(monthInt);
        if (month.length() < 2) {
            month = "0" + month;
        }
        String day = String.valueOf(d.get(Calendar.DATE));
        if (day.length() < 2) {
            day = "0" + day;
        }
        String min = "";
        String sec = "";
        String hour = "";
        if (hourInt < 10) {
            hour = "0" + hourInt;
        } else {
            hour = String.valueOf(hourInt);
        }
        if (minInt < 10) {
            min = "0" + minInt;
        } else {
            min = String.valueOf(minInt);
        }
        if (secInt < 10) {
            sec = "0" + secInt;
        } else {
            sec = String.valueOf(secInt);
        }
        return (d.get(Calendar.YEAR) + "-" + month + "-" + day + " " + hour + ":" + min + ":" + sec);
    }

    /**
     * Converts a time difference in milliseconds to Hours, Minutes, and
     * Seconds.
     *
     * @param millis Milliseconds to convert
     * @return Time in Hours, Minutes, and Seconds
     */
    public static final String convertMs(long millis) {
        long second = (millis / 1000) % 60;
        long minute = (millis / (1000 * 60)) % 60;
        long hour = (millis / (1000 * 60 * 60)) % 24;
        if (hour == 0 && minute == 0) {
            return String.format("%d seconds!", second);
        }
        if (hour == 0) {
            return String.format("%d minutes %d seconds!", minute, second);
        }
        return String.format("%d hours, %d minutes, %d seconds!", hour, minute, second);
    }

    /**
     * Capitalizes the first letter of a String
     *
     * @param str String to capitalize
     * @return Capitalized String
     */
    public static String capitalize(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return str;
        }
        return new StringBuffer(strLen).append(Character.toTitleCase(str.charAt(0))).append(str.substring(1)).toString();
    }

    /**
     * Same method as above, but only if you're bad at spelling
     *
     * @param str String to capitalize
     * @return Capitalized String
     */
    public static String capitalise(String str) {
        return capitalize(str);
    }
}
