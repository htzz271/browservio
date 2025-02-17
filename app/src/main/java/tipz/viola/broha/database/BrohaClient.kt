/*
 * Copyright (C) 2022-2023 Tipz Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tipz.viola.broha.database

import android.content.Context
import androidx.room.Room.databaseBuilder

class BrohaClient(context: Context?, dbName: String?) {
    private val appDatabase: BrohaDatabase

    init {
        appDatabase = databaseBuilder(context!!, BrohaDatabase::class.java, dbName).build()
    }

    val dao: BrohaDao?
        get() = appDatabase.brohaDao()
}