package com.ricorei.checksum;

/**
 * @author Ricorei
 */
public final class FileChecksum
{
	private final long fileSize;
	private final String sha256sum;

	public FileChecksum(long size, String hash)
	{
		this.fileSize = size;
		this.sha256sum = hash;
	}

	public long getFileSize()
	{
		return this.fileSize;
	}

	public String getSha256sum()
	{
		return this.sha256sum;
	}

	@Override
	public String toString()
	{
		return "FileChecksum{" + "fileSize=" + this.fileSize + ", sha256sum='" + this.sha256sum + '\'' + '}';
	}

	@Override
	public boolean equals(Object o)
	{
		if( this == o ) return true;
		if( o == null || getClass() != o.getClass() ) return false;

		FileChecksum fileChecksum = (FileChecksum) o;

		if( getFileSize() != fileChecksum.getFileSize() ) return false;
		return getSha256sum().equals(fileChecksum.getSha256sum());
	}

	@Override
	public int hashCode()
	{
		int result = (int) (getFileSize() ^ (getFileSize() >>> 32));
		result = 31 * result + getSha256sum().hashCode();
		return result;
	}
}
