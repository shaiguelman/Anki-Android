package com.ichi2.anki;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class BackupManager {

	static String mBackupDirectoryPath;
	static String mBrokenDirectoryPath;
	static int mMaxBackups;

	public final static int RETURN_BACKUP_CREATED = 0;
	public final static int RETURN_ERROR = 1;
	public final static int RETURN_TODAY_ALREADY_BACKUP_DONE = 2;
	public final static int RETURN_NOT_ENOUGH_SPACE = 3;
	public final static int RETURN_DECK_NOT_CHANGED = 4;
	public final static int RETURN_DECK_RESTORED = 5;

	public final static String BACKUP_SUFFIX = "/backup";
	public final static String BROKEN_DECKS_SUFFIX = "/broken";

    /* Prevent class from being instantiated */
	private BackupManager() {
	}


	private static File getBackupDirectory() {
        if (mBackupDirectoryPath == null) {
        	SharedPreferences prefs = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
    		mBackupDirectoryPath = prefs.getString("deckPath", AnkiDroidApp.getStorageDirectory()) + BACKUP_SUFFIX;
        	mMaxBackups = prefs.getInt("backupMax", 3);
        }
        File directory = new File(mBackupDirectoryPath);
        if (!directory.isDirectory()) {
        	directory.mkdirs();
        }
        return directory;
	}


	private static File getBrokenDirectory() {
        if (mBrokenDirectoryPath == null) {
        	SharedPreferences prefs = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        	mBrokenDirectoryPath = prefs.getString("deckPath", AnkiDroidApp.getStorageDirectory()) + BROKEN_DECKS_SUFFIX;
        }
        File directory = new File(mBrokenDirectoryPath);
        if (!directory.isDirectory()) {
        	directory.mkdirs();
        }
        return directory;
	}


	public static int backupDeck(String deckpath) {
        File deckFile = new File(deckpath);
        File[] deckBackups = getDeckBackups(deckFile);
        int len = deckBackups.length;
        if (len > 0 && deckBackups[len - 1].lastModified() == deckFile.lastModified()) {
    		deleteDeckBackups(deckBackups, mMaxBackups);
        	return RETURN_DECK_NOT_CHANGED;
        }
        Date value = Utils.genToday(Utils.utcOffset());
        String backupFilename = String.format(Utils.ENGLISH_LOCALE, deckFile.getName().replace(".anki", "") + "-%tF.anki", value);

        File backupFile = new File(getBackupDirectory().getPath(), backupFilename);
        if (backupFile.exists()) {
            Log.i(AnkiDroidApp.TAG, "No new backup of " + deckFile.getName() + " created. Already made one today");
    		deleteDeckBackups(deckBackups, mMaxBackups);
            return RETURN_TODAY_ALREADY_BACKUP_DONE;
        }

        if (deckFile.getUsableSpace() < deckFile.length()) {
            Log.e(AnkiDroidApp.TAG, "Not enough space on sd card to backup " + deckFile.getName() + ".");
        	return RETURN_NOT_ENOUGH_SPACE;
        }

        try {
            InputStream stream = new FileInputStream(deckFile);
            Utils.writeToFile(stream, backupFile.getAbsolutePath());
            stream.close();

            // set timestamp of file in order to avoid creating a new backup unless its changed
            backupFile.setLastModified(deckFile.lastModified());
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            Log.e(AnkiDroidApp.TAG, "Backup file " + deckFile.getName() + " - Copying of file failed.");
            return RETURN_ERROR;
        }
        deleteDeckBackups(deckBackups, mMaxBackups - 1);
        return RETURN_BACKUP_CREATED;
	}


	public static int restoreDeckBackup(String deckpath, String backupPath) {
        // rename old file and move it to subdirectory
		File deckFile = new File(deckpath);
        Date value = Utils.genToday(Utils.utcOffset());
        String movedFilename = String.format(Utils.ENGLISH_LOCALE, "to-repair-" + deckFile.getName().replace(".anki", "") + "-%tF.anki", value);
        File movedFile = new File(getBrokenDirectory().getPath(), movedFilename);
        int i = 1;
        while (movedFile.exists()) {
        	movedFile = new File(getBrokenDirectory().getPath(), movedFilename.replace(".anki", "-" + Integer.toString(i) + ".anki"));
        	i++;
        }
    	if (!deckFile.renameTo(movedFile)) {
    		return RETURN_ERROR;
    	}

    	// copy backup to new position and rename it
    	File backupFile = new File(backupPath);
    	deckFile = new File(deckpath);
        if (deckFile.getUsableSpace() < deckFile.length()) {
            Log.e(AnkiDroidApp.TAG, "Not enough space on sd card to restore " + deckFile.getName() + ".");
        	return RETURN_NOT_ENOUGH_SPACE;
        }
        try {
            InputStream stream = new FileInputStream(backupFile);
            Utils.writeToFile(stream, deckFile.getAbsolutePath());
            stream.close();

            // set timestamp of file in order to avoid creating a new backup unless its changed
            deckFile.setLastModified(backupFile.lastModified());
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            Log.e(AnkiDroidApp.TAG, "Restore of file " + deckFile.getName() + " failed.");
            return RETURN_ERROR;
        }
		return RETURN_DECK_RESTORED;
	}


	public static File[] getDeckBackups(File deckFile) {
		File[] files = getBackupDirectory().listFiles();
		ArrayList<File> deckBackups = new ArrayList<File>();
		for (File aktFile : files){
			if (aktFile.getName().replaceAll("-\\d{4}-\\d{2}-\\d{2}.anki", ".anki").equals(deckFile.getName())) {
				deckBackups.add(aktFile);
			}
		}
		Collections.sort(deckBackups);
		File[] fileList = new File[deckBackups.size()];
		deckBackups.toArray(fileList);
		return fileList;
	}


	public static boolean deleteDeckBackups(String deckpath, int keepNumber) {
		return deleteDeckBackups(getDeckBackups(new File(deckpath)), keepNumber);
	}
	public static boolean deleteDeckBackups(File deckFile, int keepNumber) {
		return deleteDeckBackups(getDeckBackups(deckFile), keepNumber);
	}
	public static boolean deleteDeckBackups(File[] deckBackups, int keepNumber) {
    	for (int i = deckBackups.length - 1 - keepNumber; i >= 0; i--) {
    		deckBackups[i].delete();
    	}
		return true;
	}


	public static boolean deleteAllBackups() {
		return DeckPicker.removeDir(getBackupDirectory());
	}

}
