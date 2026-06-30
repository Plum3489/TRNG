#include <Arduino.h>

// Demo version: uses the RP2040's built-in hardware random number generator
// instead of the external noise circuit. Output format matches the original

void setup() {
    Serial.begin(115200);
    while (!Serial) {
        delay(10);
    }

    Serial.println("TRNG starting <3 (built-in RP2040 RNG demo)");
}

void loop() {
    // hwrand32() asks the chip's hardware RNG for a 32-bit random number.
    // We only need 8 bits (one byte), so we mask off the rest.
    uint8_t randomByte = (uint8_t)(rp2040.hwrand32() & 0xFF);

    Serial.print("Random byte: 0x");
    if (randomByte < 0x10) {
        Serial.print("0");
    }
    Serial.println(randomByte, HEX);

    delay(10); // small pause so we don't overwhelm the serial connection
}