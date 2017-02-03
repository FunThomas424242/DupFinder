package de.b0n.dir.processor;

import de.b0n.dir.ClusterCallback;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Sucht in einem gegebenen Verzeichnis und dessen Unterverzeichnissen nach
 * Dateien und sortiert diese nach Dateigröße.
 * 
 * @author Claus
 *
 */
public class DuplicateLengthFinder {

	private final File folder;
	private final ExecutorService threadPool;

	private final Queue<Future<?>> futures = new ConcurrentLinkedQueue<Future<?>>();
	private final ClusterCallback<Long, File> result;

	/**
	 * Bereitet für das gegebene Verzeichnis die Suche nach gleich großen
	 * Dateien vor.
	 *
	 * @param threadPool Pool zur Ausführung der Suchen
	 * @param folder     zu durchsuchendes Verzeichnis, muss existieren und lesbar sein
	 */
	private DuplicateLengthFinder(final File folder, final ExecutorService threadPool, final ClusterCallback clusterCallback) {
		if (!folder.exists()) {
			throw new IllegalArgumentException(
					"FEHLER: Parameter <Verzeichnis> existiert nicht: " + folder.getAbsolutePath());
		}
		if (!folder.isDirectory()) {
			throw new IllegalArgumentException(
					"FEHLER: Parameter <Verzeichnis> ist kein Verzeichnis: " + folder.getAbsolutePath());
		}
		if (!folder.canRead()) {
			throw new IllegalArgumentException(
					"FEHLER: Parameter <Verzeichnis> ist nicht lesbar: " + folder.getAbsolutePath());
		}

		this.threadPool = threadPool;
		this.folder = folder;
		this.result=clusterCallback;
	}

	private ClusterCallback<Long, File> execute() {
		futures.add(threadPool.submit(new DuplicateLengthRunner(folder)));

		while (!futures.isEmpty()) {
			try {
				futures.remove().get();
			} catch (InterruptedException | ExecutionException e) {
				// This is a major problem, notify user and try to recover
				e.printStackTrace();
			}
		}
		return result;
	}

	private class DuplicateLengthRunner implements Runnable {
		private final File folder;

		public DuplicateLengthRunner(File folder) {
			this.folder = folder;
		}

		/**
		 * Iteriert durch die Elemente im Verzeichnis und legt neue Suchen für
		 * Verzeichnisse an. Dateien werden sofort der Größe nach abgelegt.
		 * Wartet die Unterverzeichnis-Suchen ab und merged deren
		 * Ergebnisdateien. Liefert das Gesamtergebnis zurück.
		 */
		@Override
		public void run() {
			for (String fileName : folder.list()) {
				File file = new File(folder.getAbsolutePath() + System.getProperty("file.separator") + fileName);
				if (!file.canRead()) {
					continue;
				}

				if (file.isDirectory()) {
					try {
						futures.add(threadPool.submit(new DuplicateLengthRunner(file)));
					} catch (IllegalArgumentException e) {
						System.err.println("Given Folder is invalid, continue with next: " + file.getAbsolutePath());
						continue;
					}
				}

                if (file.isFile()) {
                    result.addGroupedElement(Long.valueOf(file.length()), file);
                }
			}
			return;
		}
	}

    /**
     * Einstiegstmethode zum Durchsuchen eines Verzeichnisses nach Dateien
     * gleicher Größe. Verwendet einen Executors.newWorkStealingPool() als
     * ThreadPool.
     *
     * @param folder Zu durchsuchendes Verzeichnis
     * @return Liefert eine Map nach Dateigröße strukturierten Queues zurück, in
     * denen die gefundenen Dateien abgelegt sind
     */
    public static ClusterCallback<Long, File> getResult(final File folder, final ClusterCallback clusterCallback) {
        return getResult(folder, Executors.newWorkStealingPool(), clusterCallback);
    }

    /**
     * Einstiegstmethode zum Durchsuchen eines Verzeichnisses nach Dateien
     * gleicher Größe.
     *
     * @param folder     Zu durchsuchendes Verzeichnis
     * @param threadPool Pool zur Ausführung der Suchen
     * @return Liefert eine Map nach Dateigröße strukturierten Queues zurück, in
     * denen die gefundenen Dateien abgelegt sind
     */
    public static ClusterCallback<Long, File> getResult(final File folder, final ExecutorService threadPool, final ClusterCallback clusterCallback) {
        if (folder == null) {
            throw new IllegalArgumentException("folder may not be null.");
        }

        if (threadPool == null) {
            throw new IllegalArgumentException("threadPool may not be null.");
        }

        return new DuplicateLengthFinder(folder, threadPool,clusterCallback).execute().removeUniques();
    }
}

