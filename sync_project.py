import os
import shutil
import time
import subprocess
import threading
from collections import deque
from queue import Queue
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

# --- Configuration ---
SOURCE_DIR = '.' 
DEST_DIR = r"D:\Projects\Android Projects\ReadAloud\Work_Files\Synced_Files"
STRUCTURE_FILE_NAME = '_Project_Structure.txt'
COMMIT_LOG_FILE_NAME = '_Commit_Logs.txt'
DETAILED_COMMIT_LOG_FILE_NAME = '_Detailed_Commit_Logs.txt' 
EXTENSIONS_TO_COPY = ('.kt','.toml', '.xml', '.kts', '.pro', '.properties', '.md', '.txt', '.py')

# REMOVED '.git' from here so the watcher can see commit events
IGNORED_DIRS = {'build', '.idea', 'gradle'} 

# --- Retry Logic Configuration ---
RETRY_COUNT = 5
RETRY_DELAY_SECONDS = 0.2

# --- Global State ---
dest_to_source = {} 
source_to_dest = {} 
history = deque(maxlen=30)
event_queue = Queue()
metadata_update_requested = threading.Event()
history_lock = threading.Lock()

def log_and_display(message):
    """Adds a message to the history and refreshes the terminal display in a thread-safe manner."""
    with history_lock:
        timestamp = time.strftime("%H:%M:%S")
        history.append(f"[{timestamp}] {message}")
        
        os.system('cls' if os.name == 'nt' else 'clear') 
        
        print("--- Project Real-Time Sync Script (Immediate Sync Version) ---")
        print(f"Watching:   {os.path.abspath(SOURCE_DIR)}")
        print(f"Syncing to: {DEST_DIR}")
        print("Status:     Monitoring... (Press Ctrl-C to stop)")
        print("\n--- Last 30 Changes ---")
        
        for item in list(history):
            print(item)

def robust_remove(file_path):
    """Attempts to remove a file with retries on PermissionError."""
    for attempt in range(RETRY_COUNT):
        try:
            if os.path.exists(file_path):
                os.remove(file_path)
            return True
        except (PermissionError, OSError) as e:
            if attempt < RETRY_COUNT - 1:
                time.sleep(RETRY_DELAY_SECONDS)
            else:
                log_and_display(f"Failed to delete {os.path.basename(file_path)}: {e}")
                return False
    return True

def sync_commit_log(dest_dir):
    """Executes 'git log' and saves the output to a text file."""
    commit_file_path = os.path.join(dest_dir, COMMIT_LOG_FILE_NAME)
    try:
        command = ["git", "log", "--oneline", "-n", "400"]
        result = subprocess.run(
            command, capture_output=True, text=True, check=True, 
            encoding='utf-8', errors='ignore'
        )
        with open(commit_file_path, 'w', encoding='utf-8') as f:
            f.write(result.stdout)
        log_and_display(f"Updated '{COMMIT_LOG_FILE_NAME}'.")
    except Exception as e:
        log_and_display(f"Could not sync git log: {e}")

def sync_detailed_commit_log(dest_dir):
    """Executes a detailed 'git log' for the last 15 commits."""
    commit_file_path = os.path.join(dest_dir, DETAILED_COMMIT_LOG_FILE_NAME)
    try:
        command = ["git", "log", "-n", "15"] 
        result = subprocess.run(
            command, capture_output=True, text=True, check=True, 
            encoding='utf-8', errors='ignore'
        )
        with open(commit_file_path, 'w', encoding='utf-8') as f:
            f.write(result.stdout)
        log_and_display(f"Updated '{DETAILED_COMMIT_LOG_FILE_NAME}'.")
    except Exception as e:
        log_and_display(f"Could not sync detailed git log: {e}")

def update_structure_file(dest_dir):
    """(Re)generates the project structure file."""
    structure_file_path = os.path.join(dest_dir, STRUCTURE_FILE_NAME)
    try:
        with open(structure_file_path, 'w', encoding='utf-8') as f:
            f.write("# Project File Structure (Auto-Generated)\n\n")
            all_source_paths = sorted(list(source_to_dest.keys()))
            for path in all_source_paths:
                f.write(path.replace('\\', '/') + '\n')
        log_and_display(f"Updated '{STRUCTURE_FILE_NAME}'.")
    except Exception as e:
        log_and_display(f"Error updating structure file: {e}")

def get_unique_filename(source_path):
    """Generates a unique base filename, handling collisions."""
    filename = os.path.basename(source_path)
    if filename not in dest_to_source:
        return filename
    parts = os.path.normpath(source_path).split(os.sep)
    for i in range(len(parts) - 2, -1, -1):
        new_filename = f"{parts[i]}_{filename}"
        if new_filename not in dest_to_source:
            return new_filename
    return f"{os.path.splitext(filename)[0]}_{int(time.time() * 1000)}{os.path.splitext(filename)[1]}"

def sync_file(source_path, dest_dir):
    """Copies a single file to the destination with retries."""
    source_path = os.path.normpath(source_path)
    
    if not os.path.isfile(source_path) or not source_path.endswith(EXTENSIONS_TO_COPY):
        return

    if source_path not in source_to_dest:
        dest_filename_base = get_unique_filename(source_path)
        dest_to_source[dest_filename_base] = source_path
        source_to_dest[source_path] = dest_filename_base
        metadata_update_requested.set()
    else:
        dest_filename_base = source_to_dest[source_path]

    final_dest_filename = dest_filename_base + '.txt'
    dest_path = os.path.join(dest_dir, final_dest_filename)
    
    for attempt in range(RETRY_COUNT):
        try:
            shutil.copy2(source_path, dest_path)
            log_and_display(f"Synced: {os.path.basename(source_path)} -> {final_dest_filename}")
            return
        except (PermissionError, OSError) as e:
            if attempt < RETRY_COUNT - 1:
                time.sleep(RETRY_DELAY_SECONDS)
            else:
                log_and_display(f"Failed to copy {source_path}: {e}")
        except FileNotFoundError:
            log_and_display(f"Sync failed: {os.path.basename(source_path)} not found.")
            return

def delete_file(source_path, dest_dir):
    """Deletes a file from the destination and cleans up mappings."""
    source_path = os.path.normpath(source_path)
    if source_path in source_to_dest:
        dest_filename_base = source_to_dest.pop(source_path)
        dest_to_source.pop(dest_filename_base, None)
        
        final_dest_filename = dest_filename_base + '.txt'
        dest_path = os.path.join(dest_dir, final_dest_filename)
        
        if robust_remove(dest_path):
            log_and_display(f"Deleted: {os.path.basename(source_path)}")
            metadata_update_requested.set()

def initial_sync(source_dir, dest_dir):
    """Performs an authoritative full scan and sync."""
    log_and_display("Starting authoritative initial sync...")
    
    files_to_remove = [STRUCTURE_FILE_NAME, COMMIT_LOG_FILE_NAME, DETAILED_COMMIT_LOG_FILE_NAME]
    for item in os.listdir(dest_dir):
        if item.endswith('.txt') or item in files_to_remove:
            robust_remove(os.path.join(dest_dir, item))

    global dest_to_source, source_to_dest
    dest_to_source.clear()
    source_to_dest.clear()
    
    for root, dirs, files in os.walk(source_dir, topdown=True):
        # Manually exclude .git here since we removed it from IGNORED_DIRS
        dirs[:] = [d for d in dirs if d not in IGNORED_DIRS and d != '.git']
        
        for file in files:
            if file.endswith(EXTENSIONS_TO_COPY):
                sync_file(os.path.join(root, file), dest_dir)
    
    log_and_display(f"Initial sync complete. Synced {len(source_to_dest)} files.")
    metadata_update_requested.set()

class ChangeHandler(FileSystemEventHandler):
    """Filters relevant events and puts them onto a queue for immediate processing."""
    def on_any_event(self, event):
        if event.is_directory: return
        
        src_path = getattr(event, 'src_path', '')

        # --- NEW LOGIC: Detect Git Commits ---
        # If the event is inside the .git directory
        if f"{os.sep}.git{os.sep}" in src_path or src_path.endswith(f"{os.sep}.git"):
            # We look for changes to HEAD or index, which happen during a commit
            if src_path.endswith("HEAD") or src_path.endswith("index"):
                log_and_display("Git commit detected. Queuing log update...")
                metadata_update_requested.set()
            # We return here because we NEVER want to sync .git files to the destination
            return
        # -------------------------------------
        
        if any(f"{os.sep}{ignored}{os.sep}" in src_path for ignored in IGNORED_DIRS):
            return

        src_is_relevant = src_path.endswith(EXTENSIONS_TO_COPY)
        dest_is_relevant = hasattr(event, 'dest_path') and event.dest_path.endswith(EXTENSIONS_TO_COPY)

        if src_is_relevant or dest_is_relevant:
            event_queue.put(event)

def immediate_event_processor(dest_dir):
    """Pulls events from the queue and processes them one by one, immediately."""
    while True:
        event = event_queue.get()
        
        if event.event_type in ('created', 'modified'):
            sync_file(event.src_path, dest_dir)
        elif event.event_type == 'deleted':
            delete_file(event.src_path, dest_dir)
        elif event.event_type == 'moved':
            delete_file(event.src_path, dest_dir)
            sync_file(event.dest_path, dest_dir)
        
        event_queue.task_done()

def metadata_updater_worker(dest_dir):
    """Periodically updates metadata files if changes have occurred."""
    while True:
        if metadata_update_requested.wait(timeout=2.5):
            # Wait a brief moment for Git to finish writing all its files
            time.sleep(1.0) 
            update_structure_file(dest_dir)
            sync_commit_log(dest_dir)
            sync_detailed_commit_log(dest_dir) 
            metadata_update_requested.clear()

def main():
    """Main function to set up and run the monitoring."""
    os.makedirs(DEST_DIR, exist_ok=True)
    
    threading.Thread(target=immediate_event_processor, args=(DEST_DIR,), daemon=True).start()
    threading.Thread(target=metadata_updater_worker, args=(DEST_DIR,), daemon=True).start()

    initial_sync(SOURCE_DIR, DEST_DIR)

    observer = Observer()
    observer.schedule(ChangeHandler(), SOURCE_DIR, recursive=True)
    observer.start()
    
    try:
        while True: time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
    observer.join()
    print("\nMonitoring stopped.")

if __name__ == "__main__":
    main()