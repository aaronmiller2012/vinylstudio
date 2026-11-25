package com.halftonestudio;

import java.io.IOException;

/** Final SVG was parsed but some cut-safety checks could not finish. */
final class FinalCutReviewRequired extends IOException {
    FinalCutReviewRequired(String message) { super(message); }
}
