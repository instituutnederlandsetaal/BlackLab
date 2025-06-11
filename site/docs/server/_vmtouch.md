# vmtouch

::: warning Not recommended 

These days, we don't recommend using vmtouch unless you are certain you need it. Operating systems are good at managing disk cache, so leaving it to the OS will likely result in better overall performance.

:::


At the Dutch Language Institute, we used to use a tool called [vmtouch](http://hoytech.com/vmtouch/) written by Doug Hoyte to 'lock' our forward indices in the operating system's disk cache, keeping them in memory at all times. This speeds up sorting and grouping operations, as well as generating (large amounts of) KWICs (keyword-in-context results).

vmtouch is a tool that can "lock" a file in disk cache. It benefits applications that need to perform fast random access to large files (i.e. several gigabytes). Corpus search applications fall into this domain: they need random access to the "forward index" component of the index to do fast sorting and grouping.

You should be careful to ensure the machine you're using has enough RAM to keep the required files in memory permanently, and will still have memory left over for the operating system and applications.

Also important is to run vmtouch as the root user; user accounts have a limit to the amount of memory they may lock. Vmtouch will terminate with an out of memory error if it hits that limit. (it may be possible to raise this limit for a user by changing a configuration file - we haven't experimented with this)

The [official page for vmtouch](http://hoytech.com/vmtouch/) has the C source code and the online manual. In the past, we have made a slight modification to the source code to allow for larger files to be cached.

### Filesize limit

The original vmtouch has a built-in 500 MB per file limit (probably as a safety precaution). We needed to raise this limit to allow for our 2GB+ files. Hence this line:

	size_t o_max_file_size=500*1024*1024;

was changed to:

	size_t o_max_file_size=6L*1024*1024*1024;

(Please note that if you wish to cache files larger than 6 GB, you will need to change this line again)

## Running vmtouch

To run vmtouch in daemon mode, so that it will lock files in the disk cache, use the following command line:

	sudo vmtouch -vtld <list_of_files>

The switches: v=verbose, t=touch (load into disk cache), l=lock (lock in disk cache), d=daemon (keep the program running). For example, we use the following command line to keep all four forward indices of our BlackLab corpus locked in disk cache (run from within the index directory):

	sudo vmtouch -vtld fi_contents%word/tokens.dat fi_contents%lemma/tokens.dat fi_contents%pos/tokens.dat fi_contents%punct/tokens.dat

The daemon will start up and will take a while to load all files into disk cache. You can check its progress by only specifying the -v option:

	sudo vmtouch -v fi_contents%word/tokens.dat fi_contents%lemma/tokens.dat fi_contents%pos/tokens.dat fi_contents%punct/tokens.dat

## Helper scripts

We have written a few simple helper scripts for using vmtouch. These scripts are included in the vmtouch/script/ directory of the BlackLab source distribution, but they are reproduced here for convenience.

The first shell script activates the vmtouch daemon for a number of corpora under the same base directory. The second shell script checks the cache status for these files. The third is an init script you can place in /etc/init.d (under Debian) to (automatically) start/stop the daemon on a server.

### activateVmtouch.sh

```bash
#!/bin/bash
# This script will read specific large files in BlackLab indices into the
# disk cache and lock them there. This improves performance for
# applications needing random access on those files.

if [ "$EUID" -ne 0 ]
then
    echo "Please run as root"
    exit
fi

DATASETS_DIR=/blacklab/indices
VMTOUCH_BINARY=/blacklab/bin/vmtouch/vmtouch

# Figure out the list of files to cache
cd $DATASETS_DIR
FILES_TO_CACHE=
for DATASET in `ls $DATASETS_DIR`
do
    if [ -d $DATASET ]; then
    echo "  $DATASET"
    FILES_TO_CACHE="$FILES_TO_CACHE $DATASET/fi_contents%word/tokens.dat $DATASET/fi_contents%lemma/tokens.dat $DATASET/fi_contents%pos/tokens.dat $DATASET/fi_contents%punct/tokens.dat"
    fi
done

# Start vmtouch daemon to keep files in disk cache
$VMTOUCH_BINARY -vtld $FILES_TO_CACHE

### checkCache.sh

#!/bin/sh
# This script checks to see how much of the set of files to cache
# is currently in the disk cache. Should eventually reach and stay
# at 100%.

DATASETS_DIR=/blacklab/indices
VMTOUCH_BINARY=/blacklab/bin/vmtouch/vmtouch

# Figure out the list of files to cache
cd $DATASETS_DIR
FILES_TO_CACHE=
for DATASET in `ls $DATASETS_DIR`
do
    if [ -d $DATASET ]; then
    FILES_TO_CACHE="$FILES_TO_CACHE $DATASET/fi_contents%word/tokens.dat $DATASET/fi_contents%lemma/tokens.dat $DATASET/fi_contents%pos/tokens.dat $DATASET/fi_contents%punct/tokens.dat"
    fi
done

# Check to see how much of the files has been cached
$VMTOUCH_BINARY -v $FILES_TO_CACHE
```

### /etc/init.d/vmtouch (startupscript)

```bash
#!/bin/sh
### BEGIN INIT INFO
# Provides:          vmtouch
# Required-Start:    $remote_fs $syslog
# Required-Stop:     $remote_fs $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Start vmtouch at boot time
# Description:       Keeps certain files locked in the disk cache.
### END INIT INFO

# (Adapted from template at https://github.com/fhd/init-script-template/)

# Settings: dir to run from, user to run under, command to run
dir="/blacklab/bin/vmtouch"
user="root"
cmd="/blacklab/bin/vmtouch/activateVmtouch.sh"

# Daemon's name
name=`basename $0`

# Log files
stdout_log="/var/log/$name.log"
stderr_log="/var/log/$name.err"

# If vmtouch is running, return its PID.
get_pid() {
    echo `ps -ef | grep -v grep | grep "vmtouch -vtld" | awk '{print $2}' `
}

# Check to see if vmtouch is running.
is_running() {
    ps -ef | grep -v grep | grep "vmtouch -vtld" >/dev/null
}

# Start, stop, restart or show status of vmtouch, depending on commandline
case "$1" in
    start)
    if is_running; then
        echo "Already started"
    else
        echo "Starting $name"
        cd "$dir"
        sudo -u "$user" $cmd >> "$stdout_log" 2>> "$stderr_log"
        if ! is_running; then
            echo "Unable to start, see $stdout_log and $stderr_log"
            exit 1
        fi
    fi
    ;;
    stop)
    if is_running; then
        echo -n "Stopping $name.."
        kill `get_pid`
        for i in {1..10}
        do
            if ! is_running; then
                break
            fi

            echo -n "."
            sleep 1
        done
        echo

        if is_running; then
            echo "Not stopped; may still be shutting down or shutdown may have failed"
            exit 1
        else
            echo "Stopped"
        fi
    else
        echo "Not running"
    fi
    ;;
    restart)
    $0 stop
    if is_running; then
        echo "Unable to stop, will not attempt to start"
        exit 1
    fi
    $0 start
    ;;
    status)
    if is_running; then
        echo "Running"
    else
        echo "Stopped"
        exit 1
    fi
    ;;
    *)
    echo "Usage: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac

exit 0
```
