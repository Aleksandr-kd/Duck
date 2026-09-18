package com.duck.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.duckDataStore by preferencesDataStore(name = "duck_preferences")