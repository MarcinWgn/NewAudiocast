package com.wegrzyn.marcin.newaudiocast

class RadioStation (val name: String, val uri: String,val img:String,val page: String){
    override fun toString(): String {
        return  " name: $name"
    }
}