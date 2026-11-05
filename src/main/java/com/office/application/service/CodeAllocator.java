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
        int next = highest + step;
        if (next > end) {
            // range is "full" at this step size, fall back to the first unused number
            for (int c = start; c <= end; c++) {
                String s = String.valueOf(c);
                if (existing.stream().noneMatch(a -> a.code().equals(s))) return s;
            }
            throw new ValidationException("No free account codes left between " + start + " and " + end);
        }
        return String.valueOf(next);
    }
}
