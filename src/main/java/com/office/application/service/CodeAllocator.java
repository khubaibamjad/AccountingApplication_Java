package com.office.application.service;

import com.office.application.model.Account;

import java.util.List;

/** Picks the next free numeric account code inside a range. */
final class CodeAllocator {
    private CodeAllocator() { }

    static String next(List<Account> existing, int start, int end, int step) {
        int highest = existing.stream()
                .map(Account::code)
                .filter(c -> c.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .filter(c -> c >= start && c <= end)
                .max()
                .orElse(start - step);
        // prefer round numbers: 5300, then 5910, then 5911
        for (int s : new int[]{step, 10, 1}) {
            if (s <= step && highest + s <= end) return String.valueOf(highest + s);
        }
        // the top of the range is used up: take the first free number
        for (int c = start; c <= end; c++) {
            String code = String.valueOf(c);
            if (existing.stream().noneMatch(a -> a.code().equals(code))) return code;
        }
        throw new ValidationException("No free account codes left between " + start + " and " + end);
    }
}
