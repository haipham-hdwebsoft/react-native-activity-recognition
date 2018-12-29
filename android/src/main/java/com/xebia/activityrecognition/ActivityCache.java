package com.xebia.activityrecognition;

import java.util.Date;
import java.util.List;

import android.arch.persistence.room.*;
import android.os.AsyncTask;

public class ActivityCache {

    @Entity
    static public class ActivityEntry {
        @PrimaryKey(autoGenerate = true)
        public int id;

        public Date activityDateTime;

        public int activityType;
    }

    @Dao
    static public interface ActivityEntryDao {
        @Query("SELECT * FROM activityEntry")
        List<ActivityEntry> getAll();

        @Query("SELECT * FROM activityEntry WHERE activityDateTime >= :fromDateTime AND "
                + "activityDateTime <= :toDateTime ORDER BY activityDateTime ASC")
        List<ActivityEntry> getHistory(Date fromDateTime, Date toDateTime);

        @Insert
        void insert(ActivityEntry activityEntry);

        @Delete
        void delete(ActivityEntry activityEntry);

        @Query("DELETE FROM activityEntry")
        void deleteAll();
    }

    @Database(entities = { ActivityEntry.class }, version = 1, exportSchema = false)
    @TypeConverters(DateConverter.class)
    static public abstract class AppDatabase extends RoomDatabase {
        public abstract ActivityEntryDao activityEntryDao();
    }

    public static void Insert(AppDatabase appDb, ActivityEntry entity) {
        new InsertAsyncTask(appDb, entity).execute();
    }

    private static class InsertAsyncTask extends AsyncTask<Void, Void, Void> {
        private ActivityEntry entityToInsert;
        private AppDatabase appDatabase;

        private InsertAsyncTask(AppDatabase appDb, ActivityEntry entity) {
            appDatabase = appDb;
            entityToInsert = entity;
        }

        @Override
        protected Void doInBackground(Void... params) {
            appDatabase.activityEntryDao().insert(entityToInsert);
            return null;
        }
    }

    public static void GetHistory(AppDatabase appDb, Date fromDateTime, Date toDateTime,
            ActivityCache.GetHistoryAsyncResponse response) {
        new GetHistoryAsyncTask(appDb, fromDateTime, toDateTime, response).execute();
    }

    public interface GetHistoryAsyncResponse {
        public void processFinish(List<ActivityCache.ActivityEntry> output);
    }

    private static class GetHistoryAsyncTask extends AsyncTask<Void, Void, List<ActivityCache.ActivityEntry>> {
        private Date fromDateTime;
        private Date toDateTime;
        private AppDatabase appDatabase;
        public ActivityCache.GetHistoryAsyncResponse delegate = null;

        private GetHistoryAsyncTask(AppDatabase appDb, Date fDateTime, Date tDateTime,
                ActivityCache.GetHistoryAsyncResponse response) {
            appDatabase = appDb;
            fromDateTime = fDateTime;
            toDateTime = tDateTime;
            delegate = response;
        }

        @Override
        protected List<ActivityCache.ActivityEntry> doInBackground(Void... params) {
            return appDatabase.activityEntryDao().getHistory(fromDateTime, toDateTime);
        }

        @Override
        protected void onPostExecute(List<ActivityCache.ActivityEntry> result) {
            delegate.processFinish(result);
        }
    }

    public static void DeleteAll(AppDatabase appDb) {
        new InsertAsyncTask(appDb, entity).execute();
    }

    private static class DeleteAllAsyncTask extends AsyncTask<Void, Void, Void> {
        private AppDatabase appDatabase;

        private InsertAsyncTask(AppDatabase appDb) {
            appDatabase = appDb;            
        }

        @Override
        protected Void doInBackground(Void... params) {
            appDatabase.activityEntryDao().deleteAll();
            return null;
        }
    }
}