package com.cs426.learningmocha.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cs426.learningmocha.data.local.entity.ChatMessage
import com.cs426.learningmocha.data.local.entity.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getMessages(sessionId: Long): List<ChatMessage>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSession(id: Long): ChatSession?

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getMessage(id: Long): ChatMessage?

    @Insert
    suspend fun insertSession(session: ChatSession): Long

    @Insert
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun renameSession(id: Long, title: String)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)
}
