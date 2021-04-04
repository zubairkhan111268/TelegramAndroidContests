precision highp float;

uniform vec2 viewportSize;
uniform sampler2D texture;
uniform vec2 direction;

varying vec2 vTexCoord;

uniform float radius;

vec4 getTexel(vec2 offset){
	float t=1.0/256.0;
	return texture2D(texture, vTexCoord+offset*t);
}

void main(){
	gl_FragColor=getTexel(vec2(0.0));
	for(int i=1;i<=int(radius);i++){
		gl_FragColor+=getTexel(vec2(float(i))*direction);
		gl_FragColor+=getTexel(vec2(float(-i))*direction);
	}
	gl_FragColor/=radius*2.0+1.0;
}