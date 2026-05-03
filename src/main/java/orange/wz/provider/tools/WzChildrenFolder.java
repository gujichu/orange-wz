package orange.wz.provider.tools;

import orange.wz.provider.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WzChildrenFolder {
    private final List<WzFolder> folders = Collections.synchronizedList(new ArrayList<>());
    private final List<WzDirectory> wzFiles = Collections.synchronizedList(new ArrayList<>());
    private final List<WzImageFile> wzImages = Collections.synchronizedList(new ArrayList<>());
    private final List<WzMsImageFile> wzMsImages = Collections.synchronizedList(new ArrayList<>());
    private final List<WzXmlFile> wzXmlFiles = Collections.synchronizedList(new ArrayList<>());

    public List<WzFolder> getFolders() {
        synchronized (folders) {
            return new ArrayList<>(folders);
        }
    }

    public List<WzDirectory> getWzFiles() {
        synchronized (wzFiles) {
            return new ArrayList<>(wzFiles);
        }
    }

    public List<WzImageFile> getWzImages() {
        synchronized (wzImages) {
            return new ArrayList<>(wzImages);
        }
    }

    public List<WzMsImageFile> getWzMsImages() {
        synchronized (wzMsImages) {
            return new ArrayList<>(wzMsImages);
        }
    }

    public List<WzXmlFile> getWzXmlFiles() {
        synchronized (wzXmlFiles) {
            return new ArrayList<>(wzXmlFiles);
        }
    }

    public List<WzObject> getAllChildren() {
        List<WzObject> allChildren = new ArrayList<>();
        allChildren.addAll(getFolders());
        allChildren.addAll(getWzFiles());
        allChildren.addAll(getWzImages());
        allChildren.addAll(getWzMsImages());
        allChildren.addAll(getWzXmlFiles());

        return allChildren;
    }

    public void add(WzFolder folder) {
        folders.add(folder);
    }

    public void add(WzDirectory wzFile) {
        wzFiles.add(wzFile);
    }

    public void add(WzImageFile wzImage) {
        wzImages.add(wzImage);
    }

    public void add(WzMsImageFile wzMsImage) {
        wzMsImages.add(wzMsImage);
    }

    public void add(WzXmlFile wzXmlFile) {
        wzXmlFiles.add(wzXmlFile);
    }

    public boolean removeFolder(String name) {
        synchronized (folders) {
            return folders.removeIf(item -> item.getName().equalsIgnoreCase(name));
        }
    }

    public boolean removeWzFile(String name) {
        synchronized (wzFiles) {
            return wzFiles.removeIf(item -> item.getName().equalsIgnoreCase(name));
        }
    }

    public boolean removeWzImageFile(String name) {
        synchronized (wzImages) {
            return wzImages.removeIf(item -> item.getName().equalsIgnoreCase(name));
        }
    }

    public boolean removeWzMsImageFile(String name) {
        synchronized (wzMsImages) {
            return wzMsImages.removeIf(item -> item.getName().equalsIgnoreCase(name));
        }
    }

    public boolean removeWzXmlFile(String name) {
        synchronized (wzXmlFiles) {
            return wzXmlFiles.removeIf(item -> item.getName().equalsIgnoreCase(name));
        }
    }
}
