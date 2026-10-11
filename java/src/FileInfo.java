package me.p_menta.zb_game_perf;

public class FileInfo {
    public FilePrefix prefix;
    public String relativePath;
    public int line;
    
    public FileInfo(FilePrefix prefix, String relativePath) {
        this.prefix = prefix;
        this.relativePath = relativePath;
        this.line = 0;
    }
    
    public FileInfo(FilePrefix prefix, String relativePath, int line) {
        this.prefix = prefix;
        this.relativePath = relativePath;
        this.line = line;
    }

    /**
     * Formats location as Mod_Name:folder/subfolder/file:line
     * Keeps up to the last 2 parent directories before the filename, e.g.
     * ArcadiaLifestyleMP:lua/client/01_Tracker.lua:148 or ISInventoryPane.lua:120
     */
    public String getFormattedLocation() {
        return formatLocation(this.prefix, this.relativePath, this.line);
    }

    public static String formatLocation(FilePrefix prefix, String relativePath, int line) {
        if (relativePath == null || relativePath.isEmpty()) {
            return line > 0 ? ":" + line : "";
        }
        String norm = relativePath.replace('\\', '/');
        while (norm.startsWith("/")) {
            norm = norm.substring(1);
        }

        String modName = null;
        String pathInside = norm;

        if (prefix == FilePrefix.INTERNAL) {
            modName = "ZBGamePerf";
        } else if (prefix == FilePrefix.LMOD || prefix == FilePrefix.SMOD || prefix == FilePrefix.WMOD) {
            int firstSlash = norm.indexOf('/');
            if (firstSlash > 0) {
                modName = norm.substring(0, firstSlash);
                pathInside = norm.substring(firstSlash + 1);
            }
        }

        // Clean out version / media subfolder prefixes inside the mod folder
        // e.g. "42/media/lua/client/..." or "42.1/media/lua/client/..." or "media/lua/client/..."
        while (true) {
            if (pathInside.startsWith("media/")) {
                pathInside = pathInside.substring(6);
            } else if (pathInside.matches("^[0-9]+(\\.[0-9]+)*/.*")) {
                int slash = pathInside.indexOf('/');
                pathInside = pathInside.substring(slash + 1);
            } else {
                break;
            }
        }

        // Keep at most 2 parent directories before filename: e.g. "/lua/client/file.lua"
        String[] parts = pathInside.split("/");
        String shortPath;
        if (parts.length <= 3) {
            shortPath = "/" + pathInside;
        } else {
            shortPath = "/" + parts[parts.length - 3] + "/" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }

        StringBuilder sb = new StringBuilder();
        if (modName != null && !modName.isEmpty()) {
            sb.append(modName).append(':');
        }
        sb.append(shortPath);
        if (line > 0) {
            sb.append(':').append(line);
        }
        return sb.toString();
    }
}

