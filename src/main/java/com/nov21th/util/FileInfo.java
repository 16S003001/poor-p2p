package com.nov21th.util;

public class FileInfo {

        private String name;

        private String hash;

        private int size;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public FileInfo() {
        }

        public FileInfo(String name, String hash, int size) {
            this.name = name;
            this.hash = hash;
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FileInfo info = (FileInfo) o;

            if (size != info.size) return false;
            if (name != null ? !name.equals(info.name) : info.name != null) return false;
            return hash != null ? hash.equals(info.hash) : info.hash == null;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + size;
            result = 31 * result + (hash != null ? hash.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return name + '\t' + hash + "\t" + size;
        }
    }