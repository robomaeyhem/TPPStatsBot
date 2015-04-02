package tppbot;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.apache.commons.io.FileUtils;
import org.jibble.pircbot.PircBot;

enum State {

    UNKNOWN, PRE_BATTLE, TEN_SEC_LEFT, IN_BATTLE, POST_BATTLE, LEADERBOARD
}

/**
 * Stats Bot Main Class.
 *
 * @author Michael
 */
public class TPPStatsBot extends PircBot {

    private static String SB_VERSION = "1.3";
    /**
     * Sets the DEBUG flag on or off.
     */
    public boolean DEBUG = false;
    private String path = "";
    private static String BOT_NAME = "";
    private HashMap<String, Integer> redTeam;
    private HashMap<String, Integer> blueTeam;
    private HashMap<String, String> names;
    private HashMap<String, String> moves;
    private HashMap<String,Integer> cacheList;
    private int avgRed;
    private int avgBlue;
    private int totalRed;
    private int totalBlue;
    private String redPokemon, bluePokemon, longestRed, longestBlue, shortestRed, shortestBlue;
    private long timeBefore;
    private long timeAfter;
    private long longestMatch, shortestMatch, longestStart, shortestStart;
    private File logFile;
    private static boolean canExit = true;

    /**
     * Determines the State of Stats Bot dependent on what part of the match TPP
     * is in.
     */
    public State STATE = State.UNKNOWN;
    /**
     * Time the last message was received to Stats Bot.
     */
    public long LAST_MESSAGE_TIME;
    private boolean hasBet;
    private String betTeam;
    private int betAmt;
    /**
     * GUI interface for Stats Bot.
     */
    public GUI g;
    private String channel;

    /**
     * Creates a new instance of Stats Bot.
     *
     * @param botName Name of the Stats Bot for IRC
     * @param debug DEBUG flag
     */
    public TPPStatsBot(String botName, boolean debug) {
        BOT_NAME = botName;
        this.DEBUG = debug;
        path = "";
        if (DEBUG) {
            path = "D:\\a\\TPPStatsBot\\";
        }
        this.setName(BOT_NAME);
        LAST_MESSAGE_TIME = 0;
        channel = "";
        avgRed = 0;
        avgBlue = 0;
        totalRed = 0;
        totalBlue = 0;
        timeBefore = 0;
        timeAfter = 0;
        redPokemon = "";
        bluePokemon = "";
        longestRed = "";
        longestBlue = "";
        shortestRed = "";
        shortestBlue = "";
        hasBet = false;
        betTeam = "";
        betAmt = 0;
        cacheList = new HashMap<>();
        redTeam = new HashMap<>();
        blueTeam = new HashMap<>();
        names = new HashMap<>();
        moves = new HashMap<>();
        try (FileInputStream fileIn = new FileInputStream(path + "tppRecords.dat"); ObjectInputStream in = new ObjectInputStream(fileIn)) {
            longestMatch = (long) in.readObject();
            longestRed = (String) in.readObject();
            longestBlue = (String) in.readObject();
            longestStart = (long) in.readObject();
            shortestMatch = (long) in.readObject();
            shortestRed = (String) in.readObject();
            shortestBlue = (String) in.readObject();
            shortestStart = (long) in.readObject();
        } catch (Exception ex) {
            System.err.println("[WARNING] Failed to read the records list!! " + ex);
        }
        STATE = State.UNKNOWN;
        logFile = new File(path + "tpplog.txt");
        appendLog("[INFO] Bot Started.");
        this.setMessageDelay(5500);
        g = new GUI(this);
        g.showGUI();
        Thread h = new Thread(() -> {
            if (STATE == State.IN_BATTLE) {
                g.updateTime();
            } else if (STATE == State.POST_BATTLE && (timeBefore != 0 && timeAfter != 0)) {
                try {
                    Thread.sleep(500);
                } catch (Exception ex) {
                }
                g.finalizeTime();
            } else {
                g.resetTime();
            }
        });
        Thread d = new Thread(() -> {
            if (STATE == State.IN_BATTLE) {
                g.updateList();
            }
        });
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(h, 1, 1, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(d, 1, 2500, TimeUnit.MILLISECONDS);
        boolean updateAvailable = checkForUpdate();
        if (updateAvailable) {
            int doUpdate = JOptionPane.showConfirmDialog(null, "An update to Stats Bot is available! Do you want to download this update now?", "Update Available", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (doUpdate == JOptionPane.YES_OPTION) {
                getNewVersion();
                TPPStatsBot.safelyExit(0);
            }
        }
    }

    /**
     * Updates the records list by saving whats in memory to the disk.
     *
     * @return True if successful, false if not
     */
    public final boolean updateRecordsList() {
        canExit = false;
        try (FileOutputStream fileOut = new FileOutputStream(path + "tppRecords.dat"); ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(longestMatch);
            out.writeObject(longestRed);
            out.writeObject(longestBlue);
            out.writeObject(longestStart);
            out.writeObject(shortestMatch);
            out.writeObject(shortestRed);
            out.writeObject(shortestBlue);
            out.writeObject(shortestStart);
            canExit = true;
            return true;
        } catch (IOException ex) {
            System.err.println("[WARNING] Failed to save the master person list!! " + ex);
            canExit = true;
            return false;
        }
    }

    /**
     * Appends the log file with the specified text
     *
     * @param text Text to append the log with
     */
    public final void appendLog(String text) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(TPPStatsBotMain.getDateTime() + "\t" + text + "\r\n");
        } catch (IOException ex) {
            System.out.println("[ERROR] Failed to write to the log! " + ex);
        }
    }

    @Override
    public void onAction(String sender, String login, String hostname, String target, String action) {
        if (action.toLowerCase().contains(BOT_NAME.toLowerCase())) {
            g.appendChat("--------->[ACTION TO YOU] " + sender + ": " + action);
        } else {
            g.appendChat("[ACTION] " + sender + " " + action);
        }
    }

    //This is where the magic shit happens, yo
    @Override
    public void onMessage(String channel, String sender, String login, String hostname, String message) {
        this.channel = channel;
        sender = TPPStatsBotMain.capitalize(sender);
        LAST_MESSAGE_TIME = System.currentTimeMillis();
        if (message.toLowerCase().contains(BOT_NAME.toLowerCase())) {
            g.appendChat("--------->[CHAT TO YOU] " + sender + ": " + message);
            if (g.getWindowFlash()) {
                if (sender.equalsIgnoreCase("tppbankbot") || sender.equalsIgnoreCase("tppinfobot")) {
                } else {
                    g.flashWindow();
                    JTextField chatInput = g.getChatInput();
                    if (chatInput.getText().isEmpty()) {
                        chatInput.setText("@" + TPPStatsBotMain.capitalize(sender) + " ");
                        chatInput.setCaretPosition(chatInput.getText().length());
                    }
                }
            }
        }
        if (!sender.equalsIgnoreCase("tppinfobot") && !sender.equalsIgnoreCase("tppbankbot")) {
            if (!message.toLowerCase().startsWith("!move") && !message.toLowerCase().startsWith("!bet") && !message.toLowerCase().startsWith("!balance") && !message.toLowerCase().startsWith("!a") && !message.toLowerCase().startsWith("!b") && !message.toLowerCase().startsWith("!c") && !message.toLowerCase().startsWith("!d") && !message.toLowerCase().startsWith("!-")) {
                if (!message.toLowerCase().contains(BOT_NAME.toLowerCase())) {
                    g.appendChat("[CHAT] " + sender + ": " + message);
                }
            }
        }
        if (sender.equalsIgnoreCase(BOT_NAME)) {
            if (message.startsWith("!bet")) {
                processBet(message);
            }
        }
        if (sender.equalsIgnoreCase("tppinfobot")) {
            if (message.startsWith("Who's that Pokemon:")) {
                STATE = State.LEADERBOARD;
                boolean updateAvailable = checkForUpdate();
                if (updateAvailable) {
                    int doUpdate = JOptionPane.showConfirmDialog(null, "An update to Stats Bot is available! Do you want to download this update now?", "Update Available", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (doUpdate == JOptionPane.YES_OPTION) {
                        getNewVersion();
                        TPPStatsBot.safelyExit(0);
                    }
                }
            }
            if (message.equalsIgnoreCase("A new match is about to begin!")) {
                if (STATE == State.IN_BATTLE) {
                    g.appendChat("[MATCH] This last match ended in a No-Contest!!");
                    appendLog("[MATCH] This last match ended in a No-Contest!!\r\n---------------------------------------------------------------------------");
                }
                STATE = State.PRE_BATTLE;
                g.resetBetPanel();
                g.resetTeams();
                g.resetWinningTeam();
                avgRed = 0;
                avgBlue = 0;
                redTeam = new HashMap<>();
                blueTeam = new HashMap<>();
                totalRed = 0;
                totalBlue = 0;
                hasBet = false;
                betTeam = "";
                betAmt = 0;
                redPokemon = "";
                bluePokemon = "";
                names = new HashMap<>();
                moves = new HashMap<>();
                cacheList = new HashMap<>();
                System.gc();
                g.appendChat("[MATCH] New match beginning, analyzing bets...");
                appendLog("[MATCH] New Match Beginning.");
            }
            if (message.equalsIgnoreCase("Betting closes in 10 seconds")) {
                STATE = State.TEN_SEC_LEFT;
            }
            if (message.equalsIgnoreCase("Team Red won the match!") || message.equalsIgnoreCase("Team Blue won the match!") || message.equalsIgnoreCase("Match resulted in a draw!")) {
                STATE = State.POST_BATTLE;
                timeAfter = System.currentTimeMillis() - 45000;
                long timeDiff = timeAfter - timeBefore;
                g.resetBetPanel();
                if (message.equalsIgnoreCase("Match resulted in a draw!")) {
                    g.appendChat("[MATCH] Welp, this match was a draw!!");
                    appendLog("[MATCH] This match ended in a draw!!\r\n---------------------------------------------------------------------------");
                    g.updateWinningTeam("draw", true);
                    return;
                }
                String teamWin = message.split("Team ")[1].split(" ", 2)[0].toLowerCase();
                g.appendChat("[RESULT] Match End, " + teamWin + " won!");
                g.updateWinningTeam(teamWin, false);
                if (timeBefore != 0 && timeAfter != 0 && timeDiff > 0) {
                    g.appendChat("[RESULT] Time of the match is " + TPPStatsBotMain.convertMs(timeDiff));
                    appendLog("[RESULT] Time of the match is " + TPPStatsBotMain.convertMs(timeDiff));
                    if (timeDiff > longestMatch) {
                        g.appendChat("[RECORD!] This match is the longest match recorded in history by stats bot!!");
                        appendLog("[RECORD!] This match is the longest match recorded in history by stats bot!!");
                        longestRed = redPokemon;
                        longestBlue = bluePokemon;
                        longestStart = timeBefore;
                        updateRecordsList();
                    } else if (timeDiff < shortestMatch) {
                        g.appendChat("[RECORD!] This match is the shortest match recorded in history by stats bot!!");
                        appendLog("[RECORD!] This match is the shortest match recorded in history by stats bot!!");
                        shortestRed = redPokemon;
                        shortestBlue = bluePokemon;
                        shortestStart = timeBefore;
                        updateRecordsList();
                    }
                }
            }
            if (message.contains("The battle between ") && message.contains("has just begun!")) {
                STATE = State.IN_BATTLE;
                g.updateList();
                timeBefore = System.currentTimeMillis();
                g.appendChat("Match now starting!");
                appendLog("Match now starting!");
                totalRed = 0;
                totalBlue = 0;
                for (int el : redTeam.values()) {
                    totalRed += el;
                }
                for (int el : blueTeam.values()) {
                    totalBlue += el;
                }
                bluePokemon = message.split("between ")[1].split(" and")[0];
                redPokemon = message.split(" and ")[1].split(" has")[0];
                g.appendChat("[MATCH] Betting time over! Blue team has " + bluePokemon + "! Red team has " + redPokemon + "!");
                appendLog("[MATCH] Betting time over! Blue team has " + bluePokemon + "! Red team has " + redPokemon + "!");
                appendLog("[MATCH] Red Team's total is $" + totalRed);
                appendLog("[MATCH] Blue Team's total is $" + totalBlue);
                g.updateTeams(redPokemon, bluePokemon);
                double ODDS = 0;
                String upperHand = "";
                if (totalBlue > totalRed) {
                    ODDS = (double) totalBlue / (double) totalRed;
                    upperHand = "blue";
                } else if (totalRed > totalBlue) {
                    ODDS = (double) totalRed / (double) totalBlue;
                    upperHand = "red";
                }
                DecimalFormat oddsDF = new DecimalFormat(".##");
                appendLog("[MATCH] The approximate odds for this match are " + oddsDF.format(ODDS) + ":1");
                double winAmt = 0;
                DecimalFormat winDF = new DecimalFormat("$");
                winDF.setRoundingMode(RoundingMode.CEILING);
                if (hasBet) {
                    if (upperHand.equals("blue")) {
                        if (betTeam.equalsIgnoreCase("blue")) {
                            ODDS = 1 / ODDS;
                        }
                    } else {
                        if (betTeam.equalsIgnoreCase("red")) {
                            ODDS = 1 / ODDS;
                        }
                    }
                    winAmt = (int) (betAmt * ODDS);
                    if (winAmt <= 0) {
                        winAmt = 1;
                    }
                    g.appendChat("[MATCH] You will win approximately " + winDF.format(winAmt) + " should " + betTeam + " team win.");
                    appendLog("[MATCH] You will win approximately " + winDF.format(winAmt) + " should " + betTeam + " team win.");
                }
            }
        }
        if (sender.equalsIgnoreCase("tppbankbot")) {
            if (message.contains("@") && message.contains("your balance is ")) {
                String person = message.split("@", 2)[1].split(" ", 2)[0];
                person = TPPStatsBotMain.capitalize(person);
                int pBalance = Integer.parseInt(message.split(" your balance is ")[1].replace(",", ""));
                boolean found = cacheList.get(person) != null;
                if (!found) {
                    cacheList.put(person, pBalance);
                } else {
                    cacheList.replace(person, pBalance);
                }
            }
        }
        if ((!sender.equalsIgnoreCase("tppinfobot") && !sender.equalsIgnoreCase("tppbankbot") && !sender.equalsIgnoreCase("tppmodbot")) && (STATE == State.UNKNOWN || STATE == State.PRE_BATTLE || STATE == State.TEN_SEC_LEFT || STATE == State.IN_BATTLE)) {
            if (message.toLowerCase().startsWith("!move")) {
                String move = message;
                if (message.length() > 7) {
                    if (message.charAt(7) == ' ') {
                        move = "" + message.charAt(6);
                    }
                } else {
                    move = "" + message.charAt(6);
                }
                move = move.toUpperCase();
                if (move.equals("A") || move.equals("B") || move.equals("C") || move.equals("D") || move.equals("-")) {
                    if (moves.containsKey(sender)) {
                        moves.replace(sender, move);
                    } else {
                        moves.put(sender, move);
                    }
                }
            }
            if (message.toLowerCase().startsWith("!a") || message.toLowerCase().startsWith("!b") || message.toLowerCase().startsWith("!c") || message.toLowerCase().startsWith("!d") || message.toLowerCase().startsWith("!-")) {
                String move = "" + message.charAt(1); //I'm too lazy to convert char to string the real way.
                move = move.toUpperCase();
                if (moves.containsKey(sender)) {
                    moves.replace(sender, move);
                } else {
                    moves.put(sender, move);
                }
            }
        }
        if (!sender.equalsIgnoreCase(BOT_NAME)) {
            if ((STATE == State.UNKNOWN || STATE == State.PRE_BATTLE || STATE == State.TEN_SEC_LEFT) && message.startsWith("!bet") && (message.toLowerCase().contains("red") || message.toLowerCase().contains("blue"))) {
                int personAmt = 0;
                try {
                    personAmt = Integer.parseInt(message.split(" ", 2)[1].split(" ", 2)[0]);
                } catch (NumberFormatException ex) {
                    return;
                }
                String team = message.toLowerCase().split("\\d+ ")[1];
                if (team.startsWith(" ")) {
                    team = team.replaceFirst(" ", "");
                }
                if (team.startsWith("red")) {
                    if (team.length() > 3) {
                        if (team.charAt(3) == ' ') {
                            team = "red";
                        }
                    } else {
                        team = "red";
                    }
                } else if (team.startsWith("blue")) {
                    if (team.length() > 4) {
                        if (team.charAt(4) == ' ') {
                            team = "blue";
                        }
                    } else {
                        team = "blue";
                    }
                }
                if (!team.equals("red") && !team.equals("blue")) {
                    System.err.println("[DENIAL] Disallowing bet by " + sender + " for $" + personAmt + " on " + team + " team!");
                    appendLog("[DENIAL] Disallowing bet by " + sender + " for $" + personAmt + " on " + team + " team!");
                    return;
                }
                boolean hasBet = names.get(sender) != null;
                int betterAmt = TPPStatsBot.getBalance(sender);
                if (betterAmt == -1) {
                    betterAmt = 1000; //Give them $1000 incase we can't find their balance.
                }
                if(cacheList.containsKey(sender)){
                    cacheList.replace(sender, betterAmt);
                }else{
                    cacheList.put(sender, betterAmt);
                }
                if (hasBet) {
                    String betterTeam = names.get(sender);
                    if (betterTeam.equalsIgnoreCase("red")) {
                        int oldBet = redTeam.get(sender);
                        if (personAmt > oldBet && personAmt <= betterAmt) {
                            redTeam.replace(sender, personAmt);
                        } else {
                            if (personAmt != oldBet && !names.get(sender).equalsIgnoreCase("red")) {
                                System.err.println("[DENIAL] Disallowing bet by " + sender + " for $" + personAmt + " on red team! \n[DENIAL] Balance: $" + betterAmt + ", old bet Amount $" + oldBet + ", old team: " + names.get(sender));
                                appendLog("[DENIAL] Disallowing bet by " + sender + " for $" + personAmt + " on red team! Balance: $" + betterAmt + ", old bet Amount $" + oldBet + ", old team: " + names.get(sender));
                            }
                        }
                    } else if (betterTeam.equalsIgnoreCase("blue")) {
                        int oldBet = blueTeam.get(sender);
                        if (personAmt > oldBet && personAmt <= betterAmt) {
                            blueTeam.replace(sender, personAmt);
                        } else {
                            if (personAmt != oldBet && !names.get(sender).equalsIgnoreCase("blue")) {
                                System.err.println("[DENIAL] Disallowing bet by " + sender + " for $" + personAmt + " on red team! \n[DENIAL] Balance: $" + betterAmt + ", old bet Amount $" + oldBet + ", old team: " + names.get(sender));
                                appendLog("[DENIAL] Disallowing bet by " + sender + " for $" + personAmt + " on red team! Balance: $" + betterAmt + ", old bet Amount $" + oldBet + ", old team: " + names.get(sender));
                            }
                        }
                    }
                } else {
                    switch (team) {
                        case "red":
                            if (personAmt <= betterAmt) {
                                names.put(sender, team);
                                redTeam.put(sender, personAmt);
                                moves.put(sender, "-");
                            } else {
                                g.appendChat("[DENIAL] Denying bet by " + sender + " on red team for $" + personAmt + ", which is above their balance of $" + betterAmt + "!");
                                appendLog("[DENIAL] Denying bet by " + sender + " on red team for $" + personAmt + ", which is above their balance of $" + betterAmt + "!");
                            }
                            break;
                        case "blue":
                            if (personAmt <= betterAmt) {
                                names.put(sender, team);
                                blueTeam.put(sender, personAmt);
                                moves.put(sender, "-");
                            } else {
                                g.appendChat("[DENIAL] Denying bet by " + sender + " on blue team for $" + personAmt + ", which is above their balance of $" + betterAmt + "!");
                                appendLog("[DENIAL] Denying bet by " + sender + " on blue team for $" + personAmt + ", which is above their balance of $" + betterAmt + "!");
                            }
                            break;
                        default:
                            return;
                    }
                }
                getTotals();
            }
        }
    }

    /**
     * Gets the Stack Trace from an exception and returns it in String form
     *
     * @param throwable Exception to get the Stack Trace from
     * @return String form of the exception
     */
    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    /**
     * Gets the totals from the teams
     */
    public void getTotals() {
        totalRed = 0;
        totalBlue = 0;
        avgRed = 0;
        avgBlue = 0;
        for (String el : redTeam.keySet()) {
            totalRed += redTeam.get(el);
        }
        avgRed = totalRed / redTeam.keySet().size();
        for (String el : blueTeam.keySet()) {
            totalBlue += blueTeam.get(el);
        }
        avgBlue = totalBlue / blueTeam.keySet().size();
    }

    /**
     * This is the GUI Object that draws the GUI for the bot
     */
    class GUI {

        private JFrame frame;
        private JTable redBox, blueBox;
        private JTextArea textBox;
        private JPanel north, center, main, movesPanel;
        private JLabel blueTotal, redTotal, redAverage, blueAverage, odds, timer, betPanel, pokemonRed, pokemonBlue, winningTeam, blueMoves, redMoves;
        private JTextField chatInput;
        private JCheckBoxMenuItem flashMessage;
        private boolean flashWindow = true;
        private final DecimalFormat DOLLARS = new DecimalFormat("$");
        private TPPStatsBot b;

        public GUI(TPPStatsBot b) {
            this.b = b;
            frame = new JFrame();
            ArrayList<Image> img = new ArrayList<>();
            frame.setIconImages(img);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1165, 695);
            frame.setTitle("Twitch Plays Pokemon Stats Bot");
            JMenuBar menu = new JMenuBar();
            frame.setJMenuBar(menu);
            JMenu file = new JMenu("File");
            JMenu edit = new JMenu("Records");
            JMenuItem updateCheck = new JMenuItem("Check for Updates to Stats Bot...");
            updateCheck.addActionListener((ActionEvent e) -> {
                Thread t = new Thread(() -> {
                    boolean updateAvailable = b.checkForUpdate();
                    if (updateAvailable) {
                        int update = JOptionPane.showConfirmDialog(null, "An update to Stats Bot is available! Do you wish to download it now?", "Update Available", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                        if (update == JOptionPane.YES_OPTION) {
                            b.getNewVersion();
                            TPPStatsBot.safelyExit(0);
                        }
                    } else {
                        JOptionPane.showMessageDialog(null, "You have the latest version of Stats Bot.", "No Update Available", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
                t.start();
            });
            file.add(updateCheck);
            flashMessage = new JCheckBoxMenuItem("Flash on new Message", true);
            flashMessage.addActionListener((ActionEvent e) -> {
                flashWindow = flashMessage.isSelected();
            });
            file.add(flashMessage);
            JMenuItem exit = new JMenuItem("Exit");
            exit.addActionListener((ActionEvent e) -> {
                TPPStatsBot.safelyExit(0);
            });
            JMenuItem balanceLookup = new JMenuItem("Check Balance");
            balanceLookup.addActionListener((ActionEvent e) -> {
                JDialog d = new JDialog();
                d.setModal(false);
                d.setTitle("Balance Lookup");
                d.setSize(400, 105);
                d.setResizable(false);
                JPanel main = new JPanel();
                main.setLayout(new BorderLayout());
                JPanel mainUp = new JPanel();
                mainUp.setLayout(new GridLayout(2, 2));
                d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                JPanel buttons = new JPanel();
                JTextField input = new JTextField();
                JTextField output = new JTextField();
                mainUp.add(new JLabel("Name to lookup:"));
                mainUp.add(new JLabel("Balance: "));
                mainUp.add(input);
                mainUp.add(output);
                output.setEditable(false);
                JButton find = new JButton("Find");
                JButton close = new JButton("Close");
                find.addActionListener((ActionEvent ea) -> {
                    String out = "";
                    if (TPPStatsBot.getBalance(input.getText()) == -1) {
                        out = "Name not found";
                    } else {
                        out = DOLLARS.format(TPPStatsBot.getBalance(input.getText()));
                    }
                    output.setText(out);
                });
                d.getRootPane().setDefaultButton(find);
                close.addActionListener((ActionEvent ea) -> {
                    d.dispose();
                });
                buttons.add(find);
                buttons.add(close);
                main.add(mainUp, BorderLayout.CENTER);
                main.add(buttons, BorderLayout.SOUTH);
                d.add(main);
                d.setVisible(true);
            });
            JMenuItem records = new JMenuItem("Records");
            records.addActionListener((ActionEvent e) -> {
                JDialog d = new JDialog();
                d.setSize(400, 185);
                d.setResizable(false);
                d.setTitle("TPP Records");
                JPanel panel = new JPanel();
                panel.setLayout(new GridLayout(9, 9));
                panel.add(new JLabel("Longest Match Length:"));
                panel.add(new JLabel(TPPStatsBotMain.convertMs(b.longestMatch)));
                panel.add(new JLabel("Pokemon on team Blue: "));
                panel.add(new JLabel(b.longestBlue));
                panel.add(new JLabel("Pokemon on team Red: "));
                panel.add(new JLabel(b.longestRed));
                panel.add(new JLabel("Match took place at: "));
                panel.add(new JLabel(TPPStatsBotMain.getDateTime(b.longestStart)));
                panel.add(new JLabel());
                panel.add(new JLabel());
                panel.add(new JLabel("Shortest Match Length:"));
                panel.add(new JLabel(TPPStatsBotMain.convertMs(b.shortestMatch)));
                panel.add(new JLabel("Pokemon on team Blue:"));
                panel.add(new JLabel(b.shortestBlue));
                panel.add(new JLabel("Pokemon on team Red:"));
                panel.add(new JLabel(b.shortestRed));
                panel.add(new JLabel("Match took place at: "));
                panel.add(new JLabel(TPPStatsBotMain.getDateTime(b.shortestStart)));
                d.add(panel);
                d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                d.setVisible(true);
            });
            file.add(balanceLookup);
            edit.add(records);
            file.add(exit);
            menu.add(file);
            menu.add(edit);
            DOLLARS.setGroupingSize(3);
            DOLLARS.setGroupingUsed(true);
            main = new JPanel();
            JPanel south = new JPanel();
            main.setLayout(new BorderLayout());
            south.setLayout(new BorderLayout());
            redBox = new JTable();
            blueBox = new JTable();
            textBox = new JTextArea();
            textBox.setWrapStyleWord(true);
            textBox.setLineWrap(true);
            textBox.setEditable(false);
            textBox.setRows(12);
            JPanel pokemonPanel = new JPanel();
            pokemonPanel.setLayout(new GridLayout(1, 3));
            pokemonRed = new JLabel("");
            pokemonBlue = new JLabel("");
            winningTeam = new JLabel("");
            winningTeam.setHorizontalAlignment(JLabel.CENTER);
            pokemonRed.setHorizontalAlignment(JLabel.RIGHT);
            pokemonBlue.setHorizontalAlignment(JLabel.LEFT);
            pokemonPanel.add(pokemonBlue);
            pokemonPanel.add(winningTeam);
            pokemonPanel.add(pokemonRed);
            south.add(pokemonPanel, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(textBox);
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            south.add(scroll, BorderLayout.CENTER);
            JPanel chatPanel = new JPanel();
            chatInput = new JTextField();
            chatInput.setColumns(75);
            JButton send = new JButton("Send");
            send.addActionListener((ActionEvent e) -> {
                if (!chatInput.getText().isEmpty()) {
                    b.sendMessage(b.channel, chatInput.getText());
                    appendChat("[CHAT] " + BOT_NAME + ": " + chatInput.getText());
                    if (chatInput.getText().startsWith("!bet")) {
                        b.processBet(chatInput.getText());
                    }
                    if (chatInput.getText().startsWith("!move")) {
                        String message = chatInput.getText();
                        String move = "";
                        if (message.length() > 7) {
                            if (message.charAt(7) == ' ') {
                                move = "" + message.charAt(6);
                            }
                        } else {
                            move = "" + message.charAt(6);
                        }
                        move = move.toUpperCase();
                        if (b.hasBet) {
                            b.moves.replace(TPPStatsBot.BOT_NAME, move);
                        }
                    }
                    chatInput.setText("");
                }
            });
            chatPanel.add(chatInput);
            chatPanel.add(send);
            south.add(chatPanel, BorderLayout.SOUTH);
            north = new JPanel();
            center = new JPanel();
            center.setLayout(new GridLayout(3, 1));
            north.setLayout(new GridLayout(3, 3));
            JLabel timerLabel = new JLabel("Match Timer:");
            timerLabel.setHorizontalAlignment(JLabel.CENTER);
            north.add(new JLabel("Blue Team"));
            north.add(timerLabel);
            JLabel redLabel = new JLabel("Red Team");
            redLabel.setHorizontalAlignment(JLabel.RIGHT);
            north.add(redLabel);
            blueTotal = new JLabel("Total: $" + b.totalBlue);
            north.add(blueTotal);
            timer = new JLabel("");
            timer.setHorizontalAlignment(JLabel.CENTER);
            north.add(timer);
            odds = new JLabel("<html><center>Odds:<br>∞:∞</center></html>");
            odds.setHorizontalAlignment(JLabel.CENTER);
            odds.setVerticalAlignment(JLabel.TOP);
            center.add(odds);
            betPanel = new JLabel();
            betPanel.setHorizontalAlignment(JLabel.CENTER);
            blueMoves = new JLabel("");
            blueMoves.setHorizontalAlignment(JLabel.LEFT);
            redMoves = new JLabel("");
            redMoves.setHorizontalAlignment(JLabel.RIGHT);
            movesPanel = new JPanel();
            movesPanel.setLayout(new GridLayout(1, 2));
            resetMoves();
            movesPanel.add(blueMoves);
            movesPanel.add(redMoves);
            center.add(movesPanel);
            center.add(betPanel);
            redTotal = new JLabel("Total: $" + b.totalRed);
            redTotal.setHorizontalAlignment(JLabel.RIGHT);
            north.add(redTotal);
            blueAverage = new JLabel("Average: $" + avgBlue + ", Total Betters: " + blueTeam.keySet().size());
            north.add(blueAverage);
            north.add(new JLabel());
            redAverage = new JLabel("Average: $" + avgRed + ", Total Betters: " + redTeam.keySet().size());
            redAverage.setHorizontalAlignment(JLabel.RIGHT);
            north.add(redAverage);
            updateHeader();
            updateList();
            main.add(new JScrollPane(redBox), BorderLayout.EAST);
            main.add(new JScrollPane(blueBox), BorderLayout.WEST);
            main.add(south, BorderLayout.SOUTH);
            main.add(north, BorderLayout.NORTH);
            main.add(center, BorderLayout.CENTER);
            frame.add(main);
            frame.getRootPane().setDefaultButton(send);
            frame.addWindowListener(new WindowListener() {

                @Override
                public void windowOpened(WindowEvent e) {
                }

                @Override
                public void windowClosing(WindowEvent e) {
                    TPPStatsBot.safelyExit(0);
                }

                @Override
                public void windowClosed(WindowEvent e) {
                }

                @Override
                public void windowIconified(WindowEvent e) {
                }

                @Override
                public void windowDeiconified(WindowEvent e) {
                }

                @Override
                public void windowActivated(WindowEvent e) {
                }

                @Override
                public void windowDeactivated(WindowEvent e) {
                }
            });
        }

        public void showGUI() {
            frame.setVisible(true);
        }

        public void disposeGUI() {
            frame.dispose();
        }

        public boolean getWindowFlash() {
            return flashWindow;
        }

        public void setWindowFlash(boolean flash) {
            flashWindow = flash;
            flashMessage.setSelected(flash);
            flashMessage.setState(flash);
        }

        public final void updateList() {
            redBox.setModel(new TableModel() {
                private String[] columnNames = {"Name", "Bet Amount", "Balance", "Move"};
                private Object[][] data;
                private TreeMap<String, Integer> sortedCopy = ValueComparator.sortByValue(redTeam);

                {
                    this.data = new Object[redTeam.keySet().size()][4];
                    int index = 0;
                    for (String el : sortedCopy.navigableKeySet()) {
                        data[index][0] = el;
                        data[index][1] = DOLLARS.format(redTeam.get(el));
                        data[index][2] = DOLLARS.format(cacheList.get(el));
                        data[index][3] = b.moves.get(el);
                        index++;
                    }
                }

                @Override
                public int getRowCount() {
                    return redTeam.keySet().size();
                }

                @Override
                public int getColumnCount() {
                    return columnNames.length;
                }

                @Override
                public String getColumnName(int columnIndex) {
                    return columnNames[columnIndex];
                }

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return getValueAt(0, columnIndex).getClass();
                }

                @Override
                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return false;
                }

                @Override
                public Object getValueAt(int rowIndex, int columnIndex) {
                    return data[rowIndex][columnIndex];
                }

                @Override
                public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
                    //do nothing
                }

                @Override
                public void addTableModelListener(TableModelListener l) {
                }

                @Override
                public void removeTableModelListener(TableModelListener l) {
                }
            });
            blueBox.setModel(new TableModel() {
                private String[] columnNames = {"Name", "Bet Amount", "Balance", "Move"};
                private Object[][] data;
                private TreeMap<String, Integer> sortedCopy = ValueComparator.sortByValue(blueTeam);

                {
                    this.data = new Object[blueTeam.keySet().size()][4];
                    int index = 0;
                    for (String el : sortedCopy.navigableKeySet()) {
                        data[index][0] = el;
                        data[index][1] = DOLLARS.format(blueTeam.get(el));
                        data[index][2] = DOLLARS.format(cacheList.get(el));
                        data[index][3] = b.moves.get(el);
                        index++;
                    }
                }

                @Override
                public int getColumnCount() {
                    return columnNames.length;
                }

                @Override
                public String getColumnName(int columnIndex) {
                    return columnNames[columnIndex];
                }

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return getValueAt(0, columnIndex).getClass();
                }

                @Override
                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return false;
                }

                @Override
                public Object getValueAt(int rowIndex, int columnIndex) {
                    return data[rowIndex][columnIndex];
                }

                @Override
                public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
                    //do nothing
                }

                @Override
                public void addTableModelListener(TableModelListener l) {
                }

                @Override
                public void removeTableModelListener(TableModelListener l) {
                }

                @Override
                public int getRowCount() {
                    return blueTeam.keySet().size();
                }
            });
            updateHeader();
            updateMoves();
        }

        public final void appendChat(String text) {
            textBox.append(TPPStatsBotMain.getDateTime() + "|| " + text + "\n");
            textBox.setCaretPosition(textBox.getDocument().getLength());
        }

        public void changeTitle(String title) {
            frame.setTitle(title);
        }

        public final void updateHeader() {
            double ODDS = 0;
            if (totalBlue > totalRed) {
                ODDS = (double) totalBlue / (double) totalRed;
            } else if (totalRed > totalBlue) {
                ODDS = (double) totalRed / (double) totalBlue;
            }
            DecimalFormat oddsDF = new DecimalFormat(".##");
            String oddsToPrint = oddsDF.format(ODDS);
            if (oddsToPrint.equals(".0")) {
                odds.setText("<html><center>Odds:<br>∞:∞</center></html>");
            } else {
                if (totalBlue > totalRed) {
                    oddsToPrint = oddsToPrint + ":1";
                } else if (totalRed > totalBlue) {
                    oddsToPrint = "1:" + oddsToPrint;
                } else {
                    oddsToPrint = "1:1";
                }
                odds.setText("<html><center>Odds:<br>" + oddsToPrint + "</center></html>");
            }
            blueAverage.setText("Average: " + DOLLARS.format(avgBlue) + ", Total Betters: " + blueTeam.keySet().size());
            redAverage.setText("Average: " + DOLLARS.format(avgRed) + ", Total Betters: " + redTeam.keySet().size());
            redTotal.setText("Total: " + DOLLARS.format(b.totalRed));
            blueTotal.setText("Total: " + DOLLARS.format(b.totalBlue));
        }

        public final void updateTime() {
            long timeDiff = System.currentTimeMillis() - b.timeBefore;
            timer.setText(TPPStatsBotMain.convertMs(timeDiff));
        }

        public final void finalizeTime() {
            long timeDiff = b.timeAfter - b.timeBefore;
            timer.setText(TPPStatsBotMain.convertMs(timeDiff));
        }

        public final void resetTime() {
            timer.setText("");
        }

        public Toolkit getToolkit() {
            return frame.getToolkit();
        }

        public JTextField getChatInput() {
            return chatInput;
        }

        public void updateBetPanel(String team, int amt) {
            betPanel.setText("<html><center>Your bet:<br>" + DOLLARS.format(amt) + " on " + team + " team</center></html>");
        }

        public void resetBetPanel() {
            betPanel.setText("");
        }

        public void updateTeams(String redTeam, String blueTeam) {
            pokemonRed.setText(redTeam);
            pokemonBlue.setText(blueTeam);
        }

        public void resetTeams() {
            pokemonRed.setText("");
            pokemonBlue.setText("");
        }

        public void resetWinningTeam() {
            winningTeam.setText("");
        }

        public void updateWinningTeam(String teamWin, boolean draw) {
            if (draw) {
                winningTeam.setText("Match is a draw!");
            } else {
                winningTeam.setText(teamWin + " won!");
            }
        }

        public final void resetMoves() {
            blueMoves.setText("");
            redMoves.setText("");
        }
//        public final void resetMoves() {
//            blueMoves.setText("<html><div align=left>Blue team:<br>Move A: 0%<br>Move B: 0%<br>Move C: 0%<br>Move D: 0%</div></html>");
//            redMoves.setText("<html><div align=right>Red team:<br>Move A: 0%<br>Move B: 0%<br>Move C: 0%<br>Move D: 0%</div></html>");
//        }

        public final void updateMoves() {
        }

        public void flashWindow() {
            this.getToolkit().beep();
            frame.toFront();
        }
//        public final void updateMoves() {
//            double blueA = 0, blueB = 0, blueC = 0, blueD = 0, blueMoveTotal = 0;
//            double blueAtotal = 0, blueBtotal = 0, blueCtotal = 0, blueDtotal = 0, blueMovesTotal = 0;
//            double redA = 0, redB = 0, redC = 0, redD = 0, redMoveTotal = 0;
//            double redAtotal = 0, redBtotal = 0, redCtotal = 0, redDtotal = 0, redMovesTotal = 0;
//            for (String el : b.names.keySet()) {
//                String team = b.names.get(el);
//                String move = b.moves.get(el);
//                switch (team) {
//                    case "red":
//                        switch (move) {
//                            case "A":
//                                redA++;
//                                redMoveTotal++;
//                                redAtotal += b.redTeam.get(el);
//                                break;
//                            case "B":
//                                redB++;
//                                redMoveTotal++;
//                                redBtotal += b.redTeam.get(el);
//                                break;
//                            case "C":
//                                redC++;
//                                redMoveTotal++;
//                                redCtotal += b.redTeam.get(el);
//                                break;
//                            case "D":
//                                redD++;
//                                redMoveTotal++;
//                                redDtotal += b.redTeam.get(el);
//                                break;
//                            default:
//                            case "-":
//                                break;
//                        }
//                        break;
//                    case "blue":
//                        switch (move) {
//                            case "A":
//                                blueA++;
//                                blueMoveTotal++;
//                                blueAtotal += b.blueTeam.get(el);
//                                break;
//                            case "B":
//                                blueB++;
//                                blueMoveTotal++;
//                                blueBtotal += b.blueTeam.get(el);
//                                break;
//                            case "C":
//                                blueC++;
//                                blueMoveTotal++;
//                                blueCtotal += b.blueTeam.get(el);
//                                break;
//                            case "D":
//                                blueD++;
//                                blueMoveTotal++;
//                                blueDtotal += b.blueTeam.get(el);
//                                break;
//                            default:
//                            case "-":
//                                break;
//                        }
//                        break;
//                }
//            }
//            //Total all the move amounts on each team
//            redMovesTotal = redAtotal + redBtotal + redCtotal + redDtotal;
//            blueMovesTotal = blueAtotal + blueBtotal + blueCtotal + blueDtotal;
//            try {
//                blueAtotal = blueAtotal / blueMovesTotal;
//                blueBtotal = blueBtotal / blueMovesTotal;
//                blueCtotal = blueCtotal / blueMovesTotal;
//                blueDtotal = blueDtotal / blueMovesTotal;
//                blueA = (blueA * blueAtotal) / (blueMoveTotal * blueAtotal);
//                blueB = (blueB * blueBtotal) / (blueMoveTotal * blueBtotal);
//                blueC = (blueC * blueCtotal) / (blueMoveTotal * blueCtotal);
//                blueD = (blueD * blueDtotal) / (blueMoveTotal * blueDtotal);
//            } catch (ArithmeticException ex) {
//                blueA = 0;
//                blueB = 0;
//                blueC = 0;
//                blueD = 0;
//                blueAtotal = 0;
//                blueBtotal = 0;
//                blueCtotal = 0;
//                blueDtotal = 0;
//                blueMoveTotal = 0;
//            }
//            try {
//                redAtotal = redAtotal / redMovesTotal;
//                redBtotal = redBtotal / redMovesTotal;
//                redCtotal = redCtotal / redMovesTotal;
//                redDtotal = redDtotal / redMovesTotal;
//                redA = (redA * redAtotal) / (redMoveTotal * redAtotal);
//                redB = (redB * redBtotal) / (redMoveTotal * redBtotal);
//                redC = (redC * redCtotal) / (redMoveTotal * redCtotal);
//                redD = (redD * redDtotal) / (redMoveTotal * redDtotal);
//            } catch (ArithmeticException ex) {
//                redA = 0;
//                redB = 0;
//                redC = 0;
//                redD = 0;
//                redAtotal = 0;
//                redBtotal = 0;
//                redCtotal = 0;
//                redDtotal = 0;
//                redMoveTotal = 0;
//            }
//            redA = redA * 100;
//            redB = redB * 100;
//            redC = redC * 100;
//            redD = redD * 100;
//            blueA = blueA * 100;
//            blueB = blueB * 100;
//            blueC = blueC * 100;
//            blueD = blueD * 100;
//            DecimalFormat percent = new DecimalFormat();
//            percent.setRoundingMode(RoundingMode.HALF_EVEN);
//            percent.setDecimalSeparatorAlwaysShown(false);
//            blueMoves.setText("<html><div align=left>Blue team:<br>Move A: " + percent.format(blueA) + "%<br>Move B: " + percent.format(blueB) + "%<br>Move C: " + percent.format(blueC) + "%<br>Move D: " + percent.format(blueD) + "%</div></html>");
//            redMoves.setText("<html><div align=right>Red team:<br>Move A: " + percent.format(redA) + "%<br>Move B: " + percent.format(redB) + "%<br>Move C: " + percent.format(redC) + "%<br>Move D: " + percent.format(redD) + "%</div></html>");
//        }
    }


    /**
     * Processes the player bet and properly puts it into the system.
     *
     * @param message Message of the bet to process
     */
    public void processBet(String message) {
        if (STATE != State.PRE_BATTLE && STATE != State.TEN_SEC_LEFT && STATE != State.UNKNOWN) {
            return;
        }
        if (!hasBet) {
            String bufBetTeam = "";
            int bufBetAmt = 0;
            try {
                bufBetAmt = Integer.parseInt(message.split("!bet ")[1].split(" ")[0]);
                bufBetTeam = message.split("!bet ")[1].split(" ")[1];
                if (!bufBetTeam.equalsIgnoreCase("blue") && !bufBetTeam.equalsIgnoreCase("red")) {
                    throw new Exception();
                }
            } catch (Exception ex) {
                return;
            }
            if (bufBetAmt > 0 && bufBetAmt <= TPPStatsBot.getBalance(BOT_NAME)) {
                hasBet = true;
                betTeam = bufBetTeam;
                betAmt = bufBetAmt;
                names.put(BOT_NAME, betTeam.toLowerCase());
                moves.put(BOT_NAME, "-");
                switch (betTeam.toLowerCase()) {
                    case "blue":
                        blueTeam.put(BOT_NAME, betAmt);
                        break;
                    case "red":
                        redTeam.put(BOT_NAME, betAmt);
                        break;
                }
                g.updateBetPanel(betTeam, betAmt);
                return;
            } else {
                return;
            }
        } else {
            String bufBetTeam = "";
            int bufBetAmt = 0;
            try {
                bufBetAmt = Integer.parseInt(message.split("!bet ")[1].split(" ")[0]);
                bufBetTeam = message.split("!bet ")[1].split(" ")[1];
                if (!bufBetTeam.equalsIgnoreCase("blue") && !bufBetTeam.equalsIgnoreCase("red")) {
                    throw new Exception();
                }
            } catch (Exception ex) {
                return;
            }
            if (bufBetTeam.toLowerCase().equals(betTeam.toLowerCase()) && bufBetAmt > betAmt) {
                betAmt = bufBetAmt;
                switch (betTeam.toLowerCase()) {
                    case "blue":
                        blueTeam.replace(BOT_NAME, betAmt);
                        break;
                    case "red":
                        redTeam.replace(BOT_NAME, betAmt);
                        break;
                }
                g.updateBetPanel(betTeam, betAmt);
                return;
            }
        }
    }

    /**
     * Checks the website for updates to this bot.
     *
     * @return True if an update is available, false otherwise
     */
    public final boolean checkForUpdate() {
        try {
            String response = getUrlSource("http://www.michaelenfieldweather.com/tppstatsbot/versions.txt"); //once again my own website so what big whoop wanna fite about it (ง°ʖ°)ง
            response = response.split("current=")[1].split("\n", 2)[0];
            return !response.equals(SB_VERSION);
        } catch (IOException ex) {
            g.appendChat("[WARNING] Failed to check for updates! " + ex);
            ex.printStackTrace();
        } catch (ArrayIndexOutOfBoundsException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    /**
     * Opens the system web browser to the new version download page
     */
    public final void getNewVersion() {
        try {
            String url = getUrlSource("http://www.michaelenfieldweather.com/tppstatsbot/versions.txt"); //y u not fiting me yet fgt (ง°ʖ°)ง
            url = url.split("download=")[1].split("\n", 2)[0];
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            String path = new File("").getAbsolutePath();
            Runtime.getRuntime().exec("explorer.exe /select," + path);
        } catch (Exception ex) {
        }
    }

    /**
     * Returns a page's Source code as a string
     *
     * @param url Page to look up
     * @return Page Source as a String
     * @throws IOException If there was a problem getting to the webpage
     */
    public static String getUrlSource(String url) throws IOException {
        URL u = new URL(url);
        URLConnection uc = u.openConnection();
        StringBuilder a;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream(), "UTF-8"))) {
            String inputLine;
            a = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                a.append(inputLine + "\n");
            }
        }
        return a.toString();

    }

    /**
     * Safely exits Stats Bot by making sure that it's not writing the balance
     * list to the hard disk.
     *
     * @param errorCode Error code to exit Stats Bot. 0 is no error.
     */
    public static void safelyExit(int errorCode) {
        while (true) {
            if (TPPStatsBot.canExit) {
                System.exit(errorCode);
            }
        }
    }

    /**
     * Gets the balance of the user specified through TwitchPlaysLeaderboard's
     * website.
     *
     * @param username User to lookup
     * @return User's balance, -1 if there is an error
     */
    public static int getBalance(String username) {
        try {
            String response = getUrlSource("http://twitchplaysleaderboard.info/api/balance/" + username.toLowerCase());
            String success = response.split("success\":")[1].split(",", 2)[0];
            if (success.equalsIgnoreCase("true")) {
                return Integer.parseInt(response.split("\"calculated\":\\{")[1].split("amount\"\\:")[1].split(",", 2)[0]);
            } else {
                return -1;
            }
        } catch (Exception ex) {
            System.err.println("[WARNING] Failed to get the balance for " + username + "!");
            ex.printStackTrace();
            return -1;
        }
    }

}

class ValueComparator implements Comparator<String> {

    Map<String, Integer> map;

    public ValueComparator(Map<String, Integer> base) {
        this.map = base;
    }

    @Override
    public int compare(String a, String b) {
        if (map.get(a) >= map.get(b)) {
            return -1;
        } else {
            return 1;
        } // returning 0 would merge keys 
    }

    /**
     * Takes a HashMap and sorts it by Value instead of Key
     *
     * @param map HashMap to sort
     * @return TreeMap sorted by Value
     */
    public static TreeMap<String, Integer> sortByValue(HashMap<String, Integer> map) {
        ValueComparator vc = new ValueComparator(map);
        TreeMap<String, Integer> sortedMap = new TreeMap<>(vc);
        sortedMap.putAll(map);
        return sortedMap;
    }
}

//class TeamViewer implements TableModel {
//
//    private TreeMap<String, Integer> betList;
//    private ArrayList<ArrayList<Object>> data;
//    private String[] columnNames = {"Name", "Bet Amount", "Balance", "Move"};
//    private static final DecimalFormat DOLLARS = new DecimalFormat("$");
//
//    public TeamViewer(HashMap<String, Integer> betList) {
//        updateList(betList);
//    }
//
//    public final void updateList(HashMap<String, Integer> betList) {
//        this.betList = ValueComparator.sortByValue(betList);
//        int index = 0;
//        for (String el : this.betList.navigableKeySet()) {
//            data.add(new ArrayList<>());
//            data.get(index).add(el);
//            data.get(index).add(DOLLARS.format(this.betList.get(el)));
//            data.get(index).add(DOLLARS.format(TPPStatsBot.getBalance(el)));
//            data.get(index).add(TPPStatsBot.getPersonMove(el));
//            index++;
//        }
//    }
//
//    @Override
//    public int getRowCount() {
//        return data.size();
//    }
//
//    @Override
//    public int getColumnCount() {
//        return columnNames.length;
//    }
//
//    @Override
//    public String getColumnName(int columnIndex) {
//        return columnNames[columnIndex];
//    }
//
//    @Override
//    public Class<?> getColumnClass(int columnIndex) {
//        return getValueAt(0, columnIndex).getClass();
//    }
//
//    @Override
//    public boolean isCellEditable(int rowIndex, int columnIndex) {
//        return false;
//    }
//
//    @Override
//    public Object getValueAt(int rowIndex, int columnIndex) {
//        data.trimToSize();
//        return data.get(rowIndex).get(columnIndex);
//    }
//
//    @Override
//    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
//        try {
//            data.get(rowIndex).set(columnIndex, aValue);
//        } catch (ArrayIndexOutOfBoundsException ex) {
//            data.add(new ArrayList<>());
//            data.get(rowIndex).set(columnIndex, aValue);
//        }
//        data.trimToSize();
//    }
//
//    @Override
//    public void addTableModelListener(TableModelListener l) {
//    }
//
//    @Override
//    public void removeTableModelListener(TableModelListener l) {
//    }
//
//}
