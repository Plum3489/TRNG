#include <Arduino.h>

#define NOISE_PIN D10
#define SAMPLE_DELAY_US 50


uint8_t getBiasedBit(){
    while(true){
        int b1 = digitalRead(NOISE_PIN);
        delayMicroseconds(SAMPLE_DELAY_US);
        int b2 = digitalRead(NOISE_PIN);
        delayMicroseconds(SAMPLE_DELAY_US);

        if(b1 == 0 && b2 == 1) return 0;
        if(b1 == 1 && b2 == 0) return 1;
        // if theyre the same, discard
    }
}

uint8_t getRandomByte(){
    uint8_t byteVal = 0;
    for(int i = 0; i<8; i++){
        byteVal = (byteVal << 1)|getBiasedBit();
    }
    return byteVal;
}

void setup(){
    Serial.begin(115200);
    while(!Serial){
        delay(10);
    };

    pinMode(NOISE_PIN, INPUT);

    Serial.println("TRNG starting <3");
}


void loop(){
    uint8_t randomByte = getRandomByte();
    Serial.print("Random byte: 0x");
    if(randomByte < 0x10){
        Serial.print("0");
    }
    Serial.println(randomByte, HEX);
}
