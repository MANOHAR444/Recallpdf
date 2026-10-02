package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.PdfEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdfs ORDER BY dateAdded DESC")
    fun getAllPdfs(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdfs WHERE uri = :uri LIMIT 1")
    suspend fun getPdfByUri(uri: String): PdfEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfEntity)

    @Query("DELETE FROM pdfs WHERE uri = :uri")
    suspend fun deletePdf(uri: String)

    @Query("UPDATE pdfs SET lastOpenedPage = :page WHERE uri = :uri")
    suspend fun updateLastOpenedPage(uri: String, page: Int)

    @Query("UPDATE pdfs SET tags = :tags WHERE uri = :uri")
    suspend fun updatePdfTags(uri: String, tags: String)
}
