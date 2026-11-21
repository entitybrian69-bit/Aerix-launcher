#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>

unsigned int aerix_select_big_cores(const unsigned long* freqs, unsigned int core_count,
                                    bool* selected_out);

int main(void) {
    bool selected[16];

    // 1. Dual-cluster big.LITTLE (6 LITTLE + 2 Big): must pick both big cores, not just 1.
    unsigned long dual[] = {1800000, 1800000, 1800000, 1800000, 1800000, 1800000, 2400000, 2400000};
    unsigned int count = aerix_select_big_cores(dual, 8, selected);
    assert(count == 2);
    for (int i = 0; i < 6; i++) assert(!selected[i]);
    assert(selected[6] && selected[7]);

    // 2. Tri-cluster (4 LITTLE + 3 Big + 1 Prime): must pick all 4 non-LITTLE cores.
    unsigned long tri[] = {1800000, 1800000, 1800000, 1800000, 2400000, 2400000, 2400000, 3000000};
    count = aerix_select_big_cores(tri, 8, selected);
    assert(count == 4);
    for (int i = 0; i < 4; i++) assert(!selected[i]);
    for (int i = 4; i < 8; i++) assert(selected[i]);

    // 3. Uniform cluster: must pick all cores.
    unsigned long uniform[] = {2000000, 2000000, 2000000, 2000000};
    count = aerix_select_big_cores(uniform, 4, selected);
    assert(count == 4);
    for (int i = 0; i < 4; i++) assert(selected[i]);

    // 4. Unreadable cpufreq (all zeros): fallback to all cores rather than an empty set.
    unsigned long unknown[] = {0, 0, 0, 0};
    count = aerix_select_big_cores(unknown, 4, selected);
    assert(count == 4);

    // 5. Zero cores: returns 0 cleanly.
    assert(aerix_select_big_cores(unknown, 0, selected) == 0);

    puts("affinity_smoke: all big-core cluster checks passed");
    return 0;
}
