package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.PageReviewDao
import com.example.data.dao.PdfDao
import com.example.data.entity.PageReviewEntity
import com.example.data.entity.PdfEntity
import com.example.data.entity.ReviewHistoryEntity

@Database(
    entities = [
        PdfEntity::class,
        PageReviewEntity::class,
        ReviewHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pdfDao(): PdfDao
    abstract fun pageReviewDao(): PageReviewDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recall_pdf.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
