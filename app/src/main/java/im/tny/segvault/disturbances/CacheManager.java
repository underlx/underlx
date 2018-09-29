package im.tny.segvault.disturbances;

import android.content.Context;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;

public class CacheManager {
    private Context context;

    public CacheManager(Context applicationContext) {
        this.context = applicationContext;
    }

    private static class Item implements Serializable {
        public Serializable data;
        public Date storeDate;
        public String key;
    }

    public interface ItemFetcher {
        Serializable fetchItemData(String key);
    }

    public interface ItemStaleChecker {
        boolean isItemStale(String key, Date storeDate);
    }

    public interface ItemIterator<T> {
        boolean doItem(String key, Date storeDate, T data);
    }

    private Item getItem(String key) {
        Item item;
        try {
            FileInputStream fis = new FileInputStream(new File(context.getCacheDir(), buildItemFilename(key)));
            ObjectInputStream is = new ObjectInputStream(fis);
            item = (Item) is.readObject();
            is.close();
            fis.close();
        } catch (Exception e) {
            return null;
        }
        return item;
    }

    public Serializable get(String key) {
        Item item = getItem(key);
        if (item == null) {
            return null;
        }
        return item.data;
    }

    public <T> T get(String key, Class<T> valueType) {
        Serializable data = get(key);
        if (data == null) {
            return null;
        }
        try {
            return valueType.cast(data);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public Serializable get(String key, ItemStaleChecker staleChecker) {
        Item item = getItem(key);
        if (item == null) {
            return null;
        }
        if (!key.equals(item.key) || staleChecker.isItemStale(key, item.storeDate)) {
            return null;
        }
        return item.data;
    }

    public <T> T get(String key, ItemStaleChecker staleChecker, Class<T> valueType) {
        Serializable data = get(key, staleChecker);
        if (data == null) {
            return null;
        }
        try {
            return valueType.cast(data);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public Serializable getOrFetch(String key, ItemStaleChecker staleChecker, ItemFetcher fetcher) {
        Serializable data = get(key, staleChecker);
        if (data != null) {
            return data;
        }
        data = fetcher.fetchItemData(key);
        if (data != null) {
            Item item = new Item();
            item.data = data;
            item.key = key;
            putItem(item);
        }
        return data;
    }

    public <T> T getOrFetch(String key, ItemStaleChecker staleChecker, ItemFetcher fetcher, Class<T> valueType) {
        Serializable data = getOrFetch(key, staleChecker, fetcher);
        try {
            return valueType.cast(data);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public Serializable fetchOrGet(String key, ItemStaleChecker staleChecker, ItemFetcher fetcher) {
        Serializable data = fetcher.fetchItemData(key);
        if (data != null) {
            Item item = new Item();
            item.data = data;
            item.key = key;
            putItem(item);
            return data;
        }
        return get(key, staleChecker);
    }

    public <T> T fetchOrGet(String key, ItemStaleChecker staleChecker, ItemFetcher fetcher, Class<T> valueType) {
        Serializable data = fetchOrGet(key, staleChecker, fetcher);
        try {
            return valueType.cast(data);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public Date getStoreDate(String key) {
        Item item = getItem(key);
        if (item == null) {
            return null;
        }
        return item.storeDate;
    }

    private void putItem(Item item) {
        item.storeDate = new Date();
        try {
            FileOutputStream fos = new FileOutputStream(new File(context.getCacheDir(), buildItemFilename(item)));
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(item);
            os.close();
            fos.close();
        } catch (Exception e) {
            // oh well, we'll have to do without cache
            // caching is best-effort
            e.printStackTrace();
        }
    }

    public void put(String key, Serializable data) {
        Item item = new Item();
        item.data = data;
        item.storeDate = new Date();
        item.key = key;
        putItem(item);
    }

    public boolean remove(String key) {
        return new File(context.getCacheDir(), buildItemFilename(key)).delete();
    }

    public <T> void range(ItemIterator<T> iterator, Class<T> valueType) {
        File[] files = context.getCacheDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().startsWith("cm-");
            }
        });
        if (files == null) {
            return;
        }
        for (File file : files) {
            Item item = getItem(file.getName().substring("cm-".length()));
            if (item == null) {
                continue;
            }
            try {
                T data = valueType.cast(item.data);
                if (!iterator.doItem(item.key, item.storeDate, data)) {
                    return;
                }
            } catch (ClassCastException e) {
                // probably not the type this iterator is looking for
            }
        }
    }

    private String buildItemFilename(Item item) {
        return "cm-" + Integer.toString(item.key.hashCode());
    }

    private String buildItemFilename(String key) {
        return "cm-" + Integer.toString(key.hashCode());
    }
}
