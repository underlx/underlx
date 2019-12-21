package im.tny.segvault.disturbances.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "feedback")
public class Feedback {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @ColumnInfo(name = "synced")
    public boolean synced;

    @NonNull
    @ColumnInfo(name = "timestamp")
    public Date timestamp = new Date();

    @NonNull
    @ColumnInfo(name = "type")
    public String type = "";

    @NonNull
    @ColumnInfo(name = "contents")
    public String contents = "";
}
