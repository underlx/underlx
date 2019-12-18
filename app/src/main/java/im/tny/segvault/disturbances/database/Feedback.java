package im.tny.segvault.disturbances.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "feedback")
public class Feedback {
    @PrimaryKey
    @NonNull
    public String id = "";

    public boolean synced;

    @NonNull
    public Date timestamp = new Date();

    @NonNull
    public String type = "";

    @NonNull
    public String contents = "";
}
