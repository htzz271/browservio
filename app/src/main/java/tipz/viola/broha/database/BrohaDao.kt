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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface BrohaDao {
    @Query("SELECT * FROM broha")
    suspend fun getAllValues(): List<Broha>

    @Query("SELECT * FROM broha LIMIT 1")
    suspend fun isEmpty(): List<Broha>

    @Query("SELECT * FROM broha ORDER BY id DESC LIMIT 1")
    suspend fun lastUrl(): Broha

    @Query("SELECT * FROM broha WHERE id LIKE :id LIMIT 1")
    suspend fun findById(id: Int): Broha

    @Insert
    suspend fun insertAll(vararg broha: Broha)

    @Update
    suspend fun updateBroha(vararg broha: Broha)

    @Query("DELETE FROM broha WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM broha")
    suspend fun deleteAll()
}