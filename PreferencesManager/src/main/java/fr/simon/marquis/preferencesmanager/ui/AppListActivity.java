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
package fr.simon.marquis.preferencesmanager.ui;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import fr.simon.marquis.preferencesmanager.R;
import fr.simon.marquis.preferencesmanager.model.AppEntry;
import fr.simon.marquis.preferencesmanager.util.Utils;

public class AppListActivity extends ActionBarActivity implements OnQueryTextListener {
	private static final int REQUEST_CODE = 123;
	private StickyListHeadersListView listView;
	private AppAdapter mAdapter;
	private View loadingView, emptyView;
	private GetApplicationsTask task;

	private SearchView mSearchView;
	private String mCurFilter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_app_list);
		loadingView = findViewById(R.id.loadingView);
		emptyView = findViewById(R.id.emptyView);
		listView = (StickyListHeadersListView) findViewById(R.id.listView);
        listView.setDrawingListUnderStickyHeader(false);
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				if (!App.getRoot().connected()) {
					new Thread(new Runnable() {
						@Override
						public void run() {
							if (!App.getRootForce().connected()) {
								Utils.displayNoRoot(AppListActivity.this, getSupportFragmentManager());
							}
						}
					}).start();
				} else {
					AppEntry item = (AppEntry) mAdapter.getItem(arg2);

					Intent i = new Intent(AppListActivity.this, PreferencesActivity.class);
					i.putExtra("TITLE", item.getLabel());
					i.putExtra("PACKAGE_NAME", item.getApplicationInfo().packageName);

					startActivityForResult(i, REQUEST_CODE);
				}
			}
		});
		getActionBar().setTitle(Utils.applyCustomTypeFace(getString(R.string.app_name), this));
		if (savedInstanceState == null || Utils.getPreviousApps() == null) {
			startTask();
		} else {
			updateListView(Utils.getPreviousApps());
		}

		if (savedInstanceState == null) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					if (!App.getRoot().connected()) {
						Utils.displayNoRoot(AppListActivity.this, getSupportFragmentManager());
					}
				}
			}).start();
		}

	}

	/**
	 * @return true if a new task is started
	 */
	private boolean startTask() {
		if (task == null || task.isCancelled()) {
			task = new GetApplicationsTask(this);
			if (Utils.hasHONEYCOMB()) {
				task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
			} else {
				task.execute();
			}
			return true;
		}
		return false;
	}

	private void updateView(View view, boolean show, boolean animate) {
		view.setVisibility(show ? View.VISIBLE : View.GONE);
		if (animate) {
			view.startAnimation(AnimationUtils.loadAnimation(this, show ? android.R.anim.fade_in : android.R.anim.fade_out));
		}
	}

	public void updateListView(ArrayList<AppEntry> apps) {
		if (mAdapter == null) {
			mAdapter = new AppAdapter(this, apps, emptyView);
		}
		listView.setAdapter(mAdapter);
		updateView(loadingView, false, false);
		updateView(listView, true, true);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_CODE) {
			if (mAdapter != null) {
				mAdapter.notifyDataSetChanged();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.app_list_activity, menu);

		MenuItem searchItem = menu.findItem(R.id.menu_search);
		mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
		mSearchView.setQueryHint(getString(R.string.action_search));
		mSearchView.setOnQueryTextListener(this);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean show = Utils.isShowSystemApps(this);
		menu.findItem(R.id.show_system_apps).setTitle(show ? R.string.hide_system_apps : R.string.show_system_apps)
				.setIcon(show ? R.drawable.ic_action_show : R.drawable.ic_action_hide);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.show_popop:
			showPopup(false);
			break;
		case R.id.show_system_apps:
			Utils.setShowSystemApps(this, !Utils.isShowSystemApps(this));
			boolean launched = startTask();
			if (!launched) {
				Utils.setShowSystemApps(this, !Utils.isShowSystemApps(this));
			}
			supportInvalidateOptionsMenu();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	public class GetApplicationsTask extends AsyncTask<Void, Void, ArrayList<AppEntry>> {
		private Context mContext;

		public GetApplicationsTask(Context ctx) {
			this.mContext = ctx;
		}

		@Override
		protected void onPreExecute() {
			updateView(loadingView, true, true);
			updateView(listView, false, false);
			super.onPreExecute();
		}

		@Override
		protected ArrayList<AppEntry> doInBackground(Void... params) {
			return Utils.getApplications(mContext);
		}

		@Override
		protected void onPostExecute(ArrayList<AppEntry> result) {
			super.onPostExecute(result);
			updateListView(result);
			finishTask();
		}

		private void finishTask() {
			task = null;
		}

		@Override
		protected void onCancelled() {
			finishTask();
			super.onCancelled();
		}

	}

	@Override
	public boolean onQueryTextChange(String newText) {
		mCurFilter = !TextUtils.isEmpty(newText) ? newText.trim() : null;
		AppAdapter adapter = (AppAdapter) mAdapter;
		if (adapter == null) {
			return false;
		}

		adapter.setFilter(mCurFilter);
		adapter.getFilter().filter(mCurFilter);
		return true;
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		Utils.hideSoftKeyboard(this, mSearchView);
		mSearchView.clearFocus();
		return false;
	}

	@Override
	public void onBackPressed() {
		if (PreferenceManager.getDefaultSharedPreferences(this).contains("about")) {
			super.onBackPressed();
		} else {
			showPopup(true);
		}

	}

	private void showPopup(boolean exit) {
		AboutDialog newFragment = AboutDialog.newInstance(exit);
		newFragment.show(getSupportFragmentManager(), "about");
	}

}
