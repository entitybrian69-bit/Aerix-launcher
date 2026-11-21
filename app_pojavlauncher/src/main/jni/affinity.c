//
// Created by maks on 19.06.2023.
//

#define _GNU_SOURCE // we are GNU GPLv3

#include <linux/limits.h>
#include <stdbool.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sched.h>
#include <string.h>

static _Thread_local bool big_core_affine = false;

static cpu_set_t bigcore_affinity_set;
static bool has_affinity_set;

#define FREQ_MAX 256
#define MAX_PROBED_CORES 128

void bigcore_format_cpu_path(char* buffer, unsigned int cpu_core) {
    snprintf(buffer, PATH_MAX, "/sys/devices/system/cpu/cpu%i/cpufreq/cpuinfo_max_freq", cpu_core);
}

/**
 * Selects all non-LITTLE cores (every core strictly above min_freq on heterogeneous big.LITTLE /
 * tri-cluster SoCs, or all cores when frequencies are uniform). Pinning to only a single core
 * causes severe thread contention when multiple render/game threads call make_big_core_affine().
 */
unsigned int aerix_select_big_cores(const unsigned long* freqs, unsigned int core_count,
                                    bool* selected_out) {
    if (core_count == 0) return 0;
    unsigned long min_freq = 0;
    unsigned long max_freq = 0;
    bool saw_freq = false;
    for (unsigned int i = 0; i < core_count; i++) {
        unsigned long f = freqs[i];
        if (f == 0) continue;
        if (!saw_freq) {
            min_freq = f;
            max_freq = f;
            saw_freq = true;
        } else {
            if (f < min_freq) min_freq = f;
            if (f > max_freq) max_freq = f;
        }
    }
    unsigned int selected = 0;
    bool heterogeneous = saw_freq && max_freq > min_freq;
    for (unsigned int i = 0; i < core_count; i++) {
        bool pick = heterogeneous ? (freqs[i] > min_freq) : true;
        selected_out[i] = pick;
        if (pick) selected++;
    }
    return selected;
}

static void create_affinity_set() {
    char path_buffer[PATH_MAX];
    char freq_buffer[FREQ_MAX];
    unsigned long freqs[MAX_PROBED_CORES];
    bool selected[MAX_PROBED_CORES];
    unsigned int corecnt = 0;
    while (corecnt < MAX_PROBED_CORES) {
        bigcore_format_cpu_path(path_buffer, corecnt);
        int corefreqfd = open(path_buffer, O_RDONLY);
        if (corefreqfd == -1) {
            break;
        }
        ssize_t read_count = read(corefreqfd, freq_buffer, FREQ_MAX - 1);
        close(corefreqfd);
        if (read_count > 0) {
            char* discard;
            freq_buffer[read_count] = '\0';
            freqs[corecnt] = strtoul(freq_buffer, &discard, 10);
        } else {
            freqs[corecnt] = 0;
        }
        corecnt++;
    }
    unsigned int picked = aerix_select_big_cores(freqs, corecnt, selected);
    CPU_ZERO(&bigcore_affinity_set);
    if (picked == 0) {
        has_affinity_set = false;
        return;
    }
    for (unsigned int i = 0; i < corecnt && i < CPU_SETSIZE; i++) {
        if (selected[i]) {
            CPU_SET_S(i, CPU_SETSIZE, &bigcore_affinity_set);
        }
    }
    printf("bigcore: selected %u of %u CPUs for affinity set\n", picked, corecnt);
    has_affinity_set = true;
}

void make_big_core_affine() {
    if(big_core_affine) return;
    if(!has_affinity_set) create_affinity_set();
    if(!has_affinity_set) return;
    int result = sched_setaffinity(0, CPU_SETSIZE, &bigcore_affinity_set);
    if(result != 0) {
        printf("bigcore: setting affinity failed: %s\n", strerror(result));
    }else{
        printf("bigcore: forced current thread onto big core cluster\n");
        big_core_affine = true;
    }
}