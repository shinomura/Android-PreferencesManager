/*
 * Copyright (C) 2013 Simon Marquis (http://www.simon-marquis.fr)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package fr.simon.marquis.preferencesmanager.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.text.TextUtils;

import com.spazedog.lib.rootfw.container.FileStat;

import fr.simon.marquis.preferencesmanager.ui.App;
import fr.simon.marquis.preferencesmanager.ui.PreferencesActivity;
import fr.simon.marquis.preferencesmanager.util.XmlUtils;

public class PreferenceFile {

	private boolean isValidPreferenceFile = true;
	private Map<String, Object> mPreferences;
	private List<Entry<String, Object>> mList;

	public PreferenceFile() {
		super();
		mPreferences = new HashMap<String, Object>();
	}

	@SuppressWarnings("unchecked")
	public static PreferenceFile fromXml(String xml) {
		PreferenceFile preferenceFile = new PreferenceFile();

		// Check for empty files
		if (TextUtils.isEmpty(xml) || xml.trim().isEmpty()) {
			return preferenceFile;
		}

		try {
			InputStream in = new ByteArrayInputStream(xml.getBytes());
			Map<String, Object> map = XmlUtils.readMapXml(in);
			in.close();

			if (map != null) {
				preferenceFile.setPreferences(map);
				return preferenceFile;
			}
		} catch (XmlPullParserException e) {
		} catch (IOException e) {
		}

		preferenceFile.isValidPreferenceFile = false;
		return preferenceFile;
	}

	public void setPreferences(Map<String, Object> map) {
		mPreferences = map;
		mList = new ArrayList<Entry<String, Object>>(mPreferences.entrySet());
		updateSort();
	}

	public Map<String, Object> getPreferences() {
		return mPreferences;
	}

	public String toXml() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			XmlUtils.writeMapXml(mPreferences, out);
		} catch (XmlPullParserException e) {
		} catch (IOException e) {
		}
		return out.toString();
	}

	public List<Entry<String, Object>> getList() {
		if (mList == null) {
			mList = new ArrayList<Entry<String, Object>>();
		}
		return mList;
	}

	public void setList(List<Entry<String, Object>> mList) {
		this.mList = mList;
		this.mPreferences = new HashMap<String, Object>();
		for (Entry<String, Object> entry : mList) {
			mPreferences.put(entry.getKey(), entry.getValue());
		}

		updateSort();
	}

	private void updateValue(String key, Object value) {
		for (Entry<String, Object> entry : mList) {
			if (entry.getKey().equals(key)) {
				entry.setValue(value);
				break;
			}
		}
		mPreferences.put(key, value);
		updateSort();
	}

	public void removeValue(String key) {
		mPreferences.remove(key);
		for (Entry<String, Object> entry : mList) {
			if (entry.getKey().equals(key)) {
				mList.remove(entry);
				break;
			}
		}
	}

	private void createAndAddValue(String key, Object value) {
		mList.add(0, new AbstractMap.SimpleEntry<String, Object>(key, value));
		mPreferences.put(key, value);
		updateSort();
	}

	public void add(String previousKey, String newKey, Object value, boolean editMode) {
		if (TextUtils.isEmpty(newKey)) {
			return;
		}

		if (!editMode) {
			if (mPreferences.containsKey(newKey)) {
				updateValue(newKey, value);
			} else {
				createAndAddValue(newKey, value);
			}
		} else {
			if (newKey.equals(previousKey)) {
				updateValue(newKey, value);
			} else {
				removeValue(previousKey);

				if (mPreferences.containsKey(newKey)) {
					updateValue(newKey, value);
				} else {
					createAndAddValue(newKey, value);
				}
			}
		}
	}

	public static boolean saveFast(PreferenceFile prefFile, String mFile, Context ctx, String packageName) {
		return saveFast(prefFile.toXml(), mFile, ctx, packageName);
	}

	public static boolean saveSlow(String preferences, String mFile, Context ctx, String packageName) {
		if (!isValid(preferences)) {
			return false;
		}

		FileStat fs = App.getRoot().file.stat(mFile);
		java.io.File f = new java.io.File(ctx.getFilesDir(), "_temp");
		try {
			FileOutputStream outputStream = new FileOutputStream(f);
			outputStream.write(preferences.getBytes());
			outputStream.close();
			App.getRoot().file.move(f.getAbsolutePath(), mFile);
			App.getRoot().file.setPermission(mFile, "0660");
			App.getRoot().file.setOwner(mFile, fs.user() + "", fs.group() + "");
			App.getRoot().processes.kill(packageName);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Nicer way to save the preferences because we don't change permissions
	 * 
	 * @param preferences
	 * @param mFile
	 * @param ctx
	 * @param packageName
	 * @return
	 */
	public static boolean saveFast(String preferences, String mFile, Context ctx, String packageName) {
		if (!isValid(preferences)) {
			return false;
		}
		try {
			App.getRoot().file.write(mFile, preferences.replace("'", "'\"'\"'"), false);
			App.getRoot().processes.kill(packageName);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private static boolean isValid(String xml) {
		try {
			XmlUtils.readMapXml(new ByteArrayInputStream(xml.getBytes()));
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public boolean isValidPreferenceFile() {
		return isValidPreferenceFile;
	}

	public void setPreferenceFile(boolean isValidPreferenceFile) {
		this.isValidPreferenceFile = isValidPreferenceFile;
	}

	public void updateSort() {
		Collections.sort(getList(), new PreferenceComparator(PreferencesActivity.preferenceSortType));
	}

}
