package com.rom.gamandipo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Parth on 08/12/2017.
 */

@Getter
@Setter
@AllArgsConstructor
public class Audio implements Serializable {
    private String data;
    private String title;
    private String album;
    private String artist;
}
