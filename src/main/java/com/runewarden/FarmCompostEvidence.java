/* SPDX-License-Identifier: BSD-2-Clause */
/*
 * Copyright (c) 2022 LlemonDuck
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.runewarden;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Confirmed server messages; an item-use click by itself is never evidence of compost. */
final class FarmCompostEvidence
{
    // Message forms are derived from RuneLite 1.13.2 CompostTracker (BSD-2-Clause).
    private static final List<Pattern> FORMS=List.of(
        Pattern.compile("You treat the .+ with (?<type>ultra|super|)compost\\."),
        Pattern.compile("^The .+ has been treated with (?<type>ultra|super|)compost.*"),
        Pattern.compile("This .+ has already been (?:treated|fertilised) with (?<type>ultra|super|)compost(?: - the spell can't make it any more fertile)?\\."),
        Pattern.compile("This is an? .+\\. The soil has been treated with (?<type>ultra|super|)compost\\..*"));
    static String parse(String message)
    {
        if(message==null || message.length()>2000)return null;
        String plain=message.replaceAll("<[^>]*>","");
        for(Pattern form:FORMS){Matcher match=form.matcher(plain);if(match.matches())return (match.group("type")+"compost").toUpperCase(Locale.ROOT);}
        return null;
    }
    static String label(String compost)
    {return "ULTRACOMPOST".equals(compost)?"Ultracompost":"SUPERCOMPOST".equals(compost)?"Supercompost":"COMPOST".equals(compost)?"Compost":"Compost unknown";}
    static boolean valid(String compost){return List.of("COMPOST","SUPERCOMPOST","ULTRACOMPOST").contains(compost);}
}
