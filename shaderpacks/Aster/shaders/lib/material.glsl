#ifndef ASTER_MATERIAL
#define ASTER_MATERIAL
// Material values follow the installed Aster material table.
vec4 blockMaterial(int id) {
    if(id==1)return vec4(.10,0,0,0);
    if(id==2)return vec4(.90,0,0,.45);
    if(id>=3&&id<=5)return vec4(.95,0,0,.65);
    if(id==10)return vec4(.32,1,0,0);
    if(id==11)return vec4(.24,1,0,0);
    if(id==12)return vec4(.36,1,0,0);
    if(id==13)return vec4(.32,0,0,0);
    if(id==14)return vec4(.12,0,0,0);
    if(id==15)return vec4(.48,0,0,0);
    if(id==16)return vec4(.90,0,0,0);
    if(id==17)return vec4(.85,0,0,0);
    if(id==18)return vec4(.95,0,0,0);
    if(id==19)return vec4(.72,0,0,0);
    if(id==20)return vec4(.50,0,4,0);
    if(id==21)return vec4(.80,0,4,0);
    if(id==22)return vec4(.90,0,.8,0);
    if(id==23)return vec4(.50,.65,0,0);
    if(id==24)return vec4(.65,.30,0,0);
    if(id==25)return vec4(.78,.05,0,0);
    return vec4(.80,0,0,0);
}
#endif
