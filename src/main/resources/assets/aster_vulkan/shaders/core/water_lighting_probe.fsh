#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:shadows.glsl>
#include <aster_vulkan:water_caustics.glsl>
#include <aster_vulkan:water_lighting.glsl>
#include <aster_vulkan:water_volume_scattering.glsl>
layout(location=0) out vec4 fragColor;
void main() {
    float index=floor(gl_FragCoord.x);
    vec2 p=vec2(index*.213-53.0,sin(index*.71)*21.0);
    if(index >= 256.0) p+=vec2(-1531.6124,119.0645);
    float t=index*2.17,depth=.2+mod(index,57.0)*.1;
    float focus=causticFocus(p,t,depth,.035);
    float loop=abs(focus-causticFocus(p,t+1200.0,depth,.035));
    // The bounded Snell density spans 0.35..3.5 times incident receiver light.
    bool bounds=focus >= -.65001 && focus <= 2.50001;
    if(gl_FragCoord.y >= 28.0) {
        float path=index*.137;
        vec3 weights=vec3(0);
        bool positive=true;
        for(int i=0;i<16;i++) {
            float a=float(i)/16.0,b=float(i+1)/16.0;
            vec3 weight=waterVolumeSegmentBetween(min(path,48.0)*a*a,min(path,48.0)*b*b);
            positive=positive && all(greaterThanEqual(weight,vec3(0))) && all(lessThanEqual(weight,vec3(1)));
            weights+=weight;
        }
        bool integral=all(lessThan(abs(weights-(vec3(1)-underwaterAbsorption(min(path,48.0)))),vec3(.000001)));
        bool identity=all(equal(waterVolumeSegmentBetween(0.0,0.0),vec3(0)));
        bool night=waterShaftAvailability(-.1,1.0)==0.0;
        bool rain=waterShaftAvailability(.8,0.0)==0.0;
        float clear=waterShaftAvailability(index/511.0*2.0-1.0,1.0);
        bool bounded=clear>=0.0 && clear<=1.0 && waterVolumePhase(1.0)>waterVolumePhase(-1.0);
        fragColor=gl_FragCoord.y<29.0 ? vec4(positive?1:0,integral?1:0,identity?1:0,1)
            :vec4(night?1:0,rain?1:0,bounded?1:0,1);
    } else if(gl_FragCoord.y >= 24.0) {
        // Actual deep sand top and inner wall from the existing native fixture.
        // Vary X/Z on the surface; Y below61 is solid sand, not a wet receiver.
        vec3 receiver=vec3(18.25+mod(index,32.0)*.17,61.0,2.25+floor(index/32.0)*.7)-CameraWorldTime.xyz;
        WaterLightTransport wet=waterLightAt(receiver,vec3(0,1,0),.035);
        vec3 wallReceiver=vec3(24.0,62.0+mod(index,32.0)*.015,2.25+floor(index/32.0)*.7)-CameraWorldTime.xyz;
        WaterLightTransport wall=waterLightAt(wallReceiver,vec3(-1,0,0),.035);
        WaterLightTransport dry=waterLightAt(vec3(28.5,64,5.5)-CameraWorldTime.xyz,vec3(0,1,0),.035);
        vec3 air=normalize(SunDirection.xyz);
        bool covered=wet.depth>3.0 && wet.wetWeight>.99;
        bool dryGeometry=dry.depth==0.0 && dry.wetWeight==0.0;
        bool unit=abs(length(wet.direction)-1.0)<.00001 && abs(length(wet.interfaceNormal)-1.0)<.00001;
        float snellError=abs(length(cross(-wet.direction,wet.interfaceNormal))-length(cross(-air,wet.interfaceNormal))/1.333);
        bool snell=snellError<.00005;
        bool transmission=all(greaterThanEqual(wet.incidentTransmission,vec3(0))) && all(lessThanEqual(wet.incidentTransmission,vec3(1)))
            && wet.incidentTransmission.r<=wet.incidentTransmission.g && wet.incidentTransmission.g<=wet.incidentTransmission.b;
        bool focusBounds=wet.focusGain>=.14999 && wet.focusGain<=4.00001;
        bool identity=all(equal(dry.incidentTransmission,vec3(1))) && all(equal(dry.skyTransmission,vec3(1)))
            && dry.focusGain==1.0 && all(equal(dry.direction,air));
        bool sky=all(lessThan(abs(wet.skyTransmission-waterIncidentTransmission(wet.depth*1.204)),vec3(.000001)));
        bool vertical=wall.focusGain==1.0 && wall.wetWeight>.99;
        vec3 direct=waterDirectRadiance(vec3(.7,.3,1.2),wet);
        bool directOnly=all(lessThan(abs(direct-vec3(.7,.3,1.2)*wet.incidentTransmission*wet.focusGain),vec3(.000001)));
        bool zero=all(equal(waterDirectRadiance(vec3(0),wet),vec3(0)));
        bool downward=wet.direction.y>0.0 && dot(-wet.direction,wet.interfaceNormal)<0.0;
        if(gl_FragCoord.y<25.0) fragColor=vec4(covered?1:0,dryGeometry?1:0,unit?1:0,1);
        else if(gl_FragCoord.y<26.0) fragColor=vec4(snell?1:0,transmission?1:0,focusBounds?1:0,1);
        else if(gl_FragCoord.y<27.0) fragColor=vec4(identity?1:0,sky?1:0,vertical?1:0,1);
        else fragColor=vec4(directOnly?1:0,zero?1:0,downward?1:0,1);
    } else if(gl_FragCoord.y >= 20.0) {
        float plane=.2678856125+mod(index,3.0)*1.5;
        float bedDepth=.2+mod(index,47.0)*.11;
        vec3 receiver=vec3(sin(index*.37)*(32.0+index*.35),plane-bedDepth,64.0+index*.4);
        vec3 towardMirror=vec3(0,2.0*plane,0)-receiver;
        vec3 delta=underwaterPlanarReturnDelta(receiver,plane);
        float path=length(delta),planeError=abs((receiver-delta).y-plane);
        bool intersection=planeError < .0001;
        bool collinear=length(cross(normalize(towardMirror),normalize(delta))) < .00001
            && dot(delta,towardMirror) <= 0.0;
        bool bounded=path >= 0.0 && path <= length(towardMirror)+.001
            && all(equal(underwaterPlanarReturnDelta(vec3(1,plane,2),plane),vec3(0)))
            && all(equal(underwaterPlanarReturnDelta(vec3(0),0.0),vec3(0)));
        vec3 transmission=underwaterPlanarReturnTransmission(delta);
        bool composition=all(lessThan(abs(transmission-underwaterAbsorption(path*.37)*underwaterAbsorption(path*.63)),vec3(.000001)));
        // Independent fixed regression from83's captured camera/plane and
        // authored shallow-bed geometry. Its exact length is54.558899 blocks.
        float fixedPlane=.2678856125;
        vec3 fixedReceiver=vec3(8.5,fixedPlane-.887885498,-70.5);
        vec3 fixedDelta=underwaterPlanarReturnDelta(fixedReceiver,fixedPlane);
        bool fixture=abs(length(fixedDelta)-54.558899)<.0001
            && abs((fixedReceiver-fixedDelta).y-fixedPlane)<.0001;
        bool ordered=all(greaterThanEqual(transmission,vec3(0))) && all(lessThanEqual(transmission,vec3(1)))
            && transmission.r <= transmission.g && transmission.g <= transmission.b
            && all(equal(underwaterPlanarReturnTransmission(vec3(0)),vec3(1)));
        if(gl_FragCoord.y<21.0) fragColor=vec4(intersection?1:0,collinear?1:0,bounded?1:0,1);
        else if(gl_FragCoord.y<22.0) fragColor=vec4(composition?1:0,fixture?1:0,ordered?1:0,1);
        else if(gl_FragCoord.y<23.0) fragColor=vec4(path/320.0,planeError/.0001,length(fixedDelta)/96.0,1);
        else {
            vec3 oldView=normalize(vec3(0,2.0*fixedPlane,0)-fixedReceiver);
            float oldPath=.887885498/max(oldView.y,.05);
            float oldOriginError=abs((fixedReceiver+oldView*oldPath).y-fixedPlane);
            bool longPath=path>100.0;
            bool oldCapFails=longPath && any(greaterThan(underwaterAbsorption(96.0)-transmission,vec3(.000001)));
            fragColor=vec4(oldOriginError>.5?1:0,longPath?1:0,oldCapFails?1:0,1);
        }
    } else if(gl_FragCoord.y >= 17.0) {
        vec3 start=vec3(20.5,62.5,5.5);
        vec3 delta=vec3(sin(index*.137)*.15,0,2.0);
        vec3 scatter=underwaterFocusedScatter(start,delta,.035);
        bool zero=all(equal(underwaterFocusedScatter(start,vec3(0),.035),vec3(0)));
        bool dry=all(equal(underwaterFocusedScatter(vec3(28.5,66,5.5),vec3(1,0,0),.035),vec3(0)));
        vec3 albedo=waterVolumeScatteringAlbedo();
        bool albedoValid=all(greaterThanEqual(albedo,vec3(0))) && all(lessThanEqual(albedo,vec3(1)))
            && all(lessThan(abs(albedo*underwaterExtinctionCoefficient()-vec3(.015)),vec3(.000001)));
        bool bounded=all(greaterThanEqual(scatter,vec3(0))) && all(lessThanEqual(scatter,vec3(3))) && albedoValid;
        if(gl_FragCoord.y<18.0) fragColor=vec4(zero?1:0,dry?1:0,bounded?1:0,1);
        else if(gl_FragCoord.y<19.0) fragColor=vec4(scatter*16.0,1);
        else {
            float path=index*.137;
            vec3 sum=vec3(0);
            for(int i=0;i<4;i++) sum+=waterVolumeSegmentWeight(path,i);
            bool weightsMatch=all(lessThan(abs(sum-(vec3(1)-underwaterAbsorption(min(path,16.0)))),vec3(.000001)));
            float phase=waterVolumePhase(index/511.0*2.0-1.0);
            bool phaseBounded=phase>=.1872 && phase<=7.655;
            fragColor=vec4(any(greaterThan(scatter,vec3(.00001)))?1:0,weightsMatch?1:0,phaseBounded?1:0,1);
        }
    } else if(gl_FragCoord.y < 1.0) {
        bool gated=causticVisibility(0.0,.02,1.0,1.0,1.0,1.0)==0.0
            && causticVisibility(6.0,.02,1.0,1.0,1.0,1.0)==0.0
            && causticVisibility(depth,.02,-.2,1.0,1.0,1.0)==0.0
            && causticVisibility(depth,.02,1.0,0.0,1.0,1.0)==0.0
            && causticVisibility(depth,.02,1.0,1.0,0.0,1.0)==0.0
            && causticVisibility(depth,.02,1.0,1.0,1.0,-1.0)==0.0;
        fragColor=vec4(bounds ? 1:0,loop < .003 ? 1:0,gated ? 1:0,1);
    } else if(gl_FragCoord.y < 2.0) {
        vec3 tint=vec3(.12,.35,.65);
        vec3 near=waterAbsorption(depth,tint),far=waterAbsorption(depth+1.0,tint);
        bool optics=all(equal(waterAbsorption(0.0,tint),vec3(1))) && all(greaterThanEqual(near,far)) && all(greaterThanEqual(far,vec3(0)));
        float sy;
        // Actual private fixture occupancy: sand at Y60, water Y61..64.
        bool wet=receiverWaterDepth(vec3(20.5,61.0,5.5),vec3(0,1,0),sy)>3.0;
        bool dry=receiverWaterDepth(vec3(28.5,64.0,5.5),vec3(0,1,0),sy)==0.0;
        fragColor=vec4(optics ? 1:0,wet ? 1:0,dry ? 1:0,1);
    } else if(gl_FragCoord.y < 3.0) {
        vec3 sunlight=normalize(SkySun.xyz);
        vec2 cloudUV=vec2(atan(sunlight.z,sunlight.x)/(2.0*SKY_PI)+.5,.5-asin(clamp(sunlight.y,-1.0,1.0))/SKY_PI);
        fragColor=vec4((focus+.65)/3.15,texture(SkyClouds,cloudUV).a,0,1);
    } else if(gl_FragCoord.y >= 15.0) {
        vec3 world=vec3(54.25+mod(index,32.0)*.25,64.0,42.25+floor(index/32.0)*.5);
        float surface,farDepth=receiverWaterDepth(world,vec3(0,1,0),surface);
        float focus=causticReceiverFocus(world.xz,farDepth);
        float path=farDepth/max(abs(refract(-normalize(SkySun.xyz),vec3(0,1,0),1.0/1.333).y),.35);
        vec3 result=waterCausticReceiver(vec3(1),world-CameraWorldTime.xyz,vec3(0,1,0),.035);
        float gain=result.b/max(waterIncidentTransmission(path).b,.0001);
        bool covered=farDepth>.85 && farDepth<.92 && waterReceiverCoverage(world.xz)==0.0 && farWaterReceiverCoverage(world.xz)>.99;
        bool bounded=focus>=-.65001 && focus<=2.50001 && gain>=.149 && gain<=4.001;
        float drySurface;
        bool dry=receiverWaterDepth(vec3(56.5,63.5,43.5),vec3(0,1,0),drySurface)==0.0
            && all(equal(waterCausticReceiver(vec3(1),vec3(56.5,63.5,43.5)-CameraWorldTime.xyz,vec3(0,1,0),.035),vec3(1)));
        fragColor=gl_FragCoord.y<16.0 ? vec4(covered?1:0,bounded?1:0,dry?1:0,1)
            : vec4((focus+.65)/3.15,gain/4.0,farDepth/8.0,1);
    } else if(gl_FragCoord.y >= 13.0) {
        // Real source water east of the fine atlas, not an assumed global sea.
        vec3 world=vec3(54.25+mod(index,32.0)*.25,64.0,42.25+floor(index/32.0)*.5);
        float surface,farDepth=receiverWaterDepth(world,vec3(0,1,0),surface);
        // Preserve the absorption-only contract on a wet vertical orientation;
        // new rows above separately validate the newly enabled far bed focus.
        vec3 result=waterCausticReceiver(vec3(1),world-CameraWorldTime.xyz,vec3(1,0,0),.035);
        float path=farDepth/max(abs(refract(-normalize(SkySun.xyz),vec3(0,1,0),1.0/1.333).y),.35);
        bool wet=farDepth>.85 && farDepth<.92 && surface>64.87 && surface<64.91;
        bool absorption=all(lessThan(abs(result-waterIncidentTransmission(path)),vec3(.002)))
            && waterReceiverCoverage(world.xz)==0.0;
        float drySurface;
        bool dry=receiverWaterDepth(vec3(72.5,64.0,43.5),vec3(0,1,0),drySurface)==0.0;
        fragColor=gl_FragCoord.y<14.0 ? vec4(wet?1:0,absorption?1:0,dry?1:0,1)
            : vec4(farDepth/8.0,waterReceiverCoverage(world.xz),farDepth>0?1:0,1);
    } else if(gl_FragCoord.y >= 11.0) {
        // The real inner wall at X24 borders the deep water column X23.
        // Absorption must work there while upward-only focusing stays zero.
        vec3 world=vec3(24.0,62.0+mod(index,32.0)*.015,2.25+floor(index/32.0)*.7);
        vec3 normal=vec3(-1,0,0);
        float surface,wetDepth=receiverWaterDepth(world,normal,surface);
        float path=wetDepth/max(abs(refract(-normalize(SkySun.xyz),vec3(0,1,0),1.0/1.333).y),.35);
        vec3 result=waterCausticReceiver(vec3(1),world-CameraWorldTime.xyz,normal,.035);
        vec3 expected=waterIncidentTransmission(path);
        vec3 dry=waterCausticReceiver(vec3(1),vec3(28.5,62.0,5.5)-CameraWorldTime.xyz,normal,.035);
        bool vertical=wetDepth>2.0 && all(lessThan(abs(result-expected),vec3(.002)))
            && causticVisibility(wetDepth,.035,SkySun.y,1.0,1.0,normal.y)==0.0;
        bool dryIdentity=all(equal(dry,vec3(1)));
        bool incident=all(equal(waterIncidentTransmission(0.0),vec3(1)))
            && all(greaterThanEqual(expected,waterIncidentTransmission(path+1.0)));
        fragColor=gl_FragCoord.y < 12.0 ? vec4(vertical?1:0,dryIdentity?1:0,incident?1:0,1) : vec4(result,1);
    } else {
        // Sample real connected-water receivers in the private fixture. The
        // old lens contracts above remain intact; these rows measure the
        // actual cache-to-receiver transfer, including all visibility gates.
        // Preserve the original two deep diagnostic rows, and add separate
        // rows at the real shallow sand top (Y64) and intermediate top (Y63).
        int band=gl_FragCoord.y < 5.0 ? 0 : gl_FragCoord.y < 8.0 ? 1 : 2;
        vec3 world=band==0 ? vec3(18.25+mod(index,32.0)*.17,61.0,2.25+floor(index/32.0)*.7)
            : band==1 ? vec3(11.15+mod(index,32.0)*.105,64.0,2.25+floor(index/32.0)*.7)
            : vec3(15.15+mod(index,32.0)*.085,63.0,2.25+floor(index/32.0)*.7);
        vec3 receiver=world-CameraWorldTime.xyz;
        float surface,receiverDepth=receiverWaterDepth(world,vec3(0,1,0),surface);
        float cached=causticReceiverFocus(world.xz,receiverDepth);
        float path=receiverDepth/max(abs(refract(-normalize(SkySun.xyz),vec3(0,1,0),1.0/1.333).y),.35);
        vec3 result=waterCausticReceiver(vec3(1),receiver,vec3(0,1,0),.035);
        float gain=result.b/max(waterIncidentTransmission(path).b,.0001);
        float row=floor(gl_FragCoord.y);
        if(row==3.0 || row==5.0 || row==8.0) fragColor=vec4((cached+.65)/3.15,
            causticVisibility(receiverDepth,.035,SkySun.y,AtmosphereColor.a,1.0,1.0),gain/4.0,1);
        else if(row==4.0 || row==6.0 || row==9.0) fragColor=vec4(result/4.0,1);
        else fragColor=vec4(receiverDepth/8.0,waterReceiverCoverage(world.xz),receiverDepth>0.0 ? 1.0 : 0.0,1);
    }
}
