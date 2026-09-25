package org.comboship.android;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Storage selection is restricted to this package's own internal and SD-card folders. */
public final class StorageLocations {
    private static final String PREFS="combo-storage", KEY="root", MARKER=".comboship-data-v1";
    public static final class Location {
        public final String label;
        public final File directory;
        Location(String label,File directory){this.label=label;this.directory=directory;}
    }
    private StorageLocations() {}
    public static List<Location> available(Context context) throws IOException {
        LinkedHashMap<String,Location> locations=new LinkedHashMap<>();
        File internal=new File(context.getFilesDir(),"comboship").getCanonicalFile();
        locations.put(internal.getPath(),new Location("Internal app storage",internal));
        for(File base:context.getExternalFilesDirs(null)) {
            if(base==null || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(base)))continue;
            File path=new File(base,"comboship").getCanonicalFile();
            String label=Environment.isExternalStorageRemovable(base)?"SD card":"Shared internal storage";
            locations.put(path.getPath(),new Location(label,path));
        }
        return new ArrayList<>(locations.values());
    }
    public static File current(Context context) throws IOException {
        List<Location> locations=available(context);
        String saved=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,locations.get(0).directory.getPath());
        for(Location location:locations)if(location.directory.getPath().equals(saved)) {
            if(!location.directory.isDirectory() && !location.directory.mkdirs())throw new IOException("Cannot create the data folder.");
            File marker=new File(location.directory,MARKER);
            if(!marker.exists() && !marker.createNewFile())throw new IOException("Cannot identify the data folder.");
            return location.directory;
        }
        throw new IOException("The selected storage is unavailable. Reinsert the SD card before playing.");
    }
    public static boolean hasData(File target){return new File(target,MARKER).isFile();}
    public static void select(Context context,File source,File requested,boolean useExisting) throws IOException {
        File target=requested.getCanonicalFile();
        boolean allowed=false;
        for(Location location:available(context))if(target.equals(location.directory))allowed=true;
        if(!allowed)throw new IOException("That storage is not available to this app.");
        if(source!=null && source.getCanonicalFile().equals(target))return;
        if(useExisting) {
            if(!hasData(target))throw new IOException("The selected folder has no ComboShip data.");
            try(DataLock ignored=new DataLock(target)){saveSelection(context,target);}
            return;
        }
        if(source==null)throw new IOException("Reinsert the selected SD card to copy its data, or select a location that already contains ComboShip data.");
        StorageCopy.prepare(source,target);
        saveSelection(context,target);
        // The old copy is deliberately preserved until the user has tested the new storage.
    }
    private static void saveSelection(Context context,File target)throws IOException {
        if(!context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,target.getPath()).commit())
            throw new IOException("Could not save the storage selection; the original copy remains available.");
    }
}
