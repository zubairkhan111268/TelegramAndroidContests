precision highp float;

uniform vec2 viewportSize;
uniform vec3 colors[4];
uniform vec2 points[4];
uniform float sizeFactor;

varying vec2 vTexCoord;

// Converts a color from linear light gamma to sRGB gamma
vec3 fromLinear(vec3 linearRGB)
{
	bvec3 cutoff = lessThan(linearRGB, vec3(0.0031308));
	vec3 higher = vec3(1.055)*pow(linearRGB, vec3(1.0/2.4)) - vec3(0.055);
	vec3 lower = linearRGB * vec3(12.92);

	return mix(higher, lower, vec3(cutoff));
}

// Converts a color from sRGB gamma to linear light gamma
vec3 toLinear(vec3 sRGB)
{
	bvec3 cutoff = lessThan(sRGB, vec3(0.04045));
	vec3 higher = pow((sRGB + vec3(0.055))/vec3(1.055), vec3(2.4));
	vec3 lower = sRGB/vec3(12.92);

	return mix(higher, lower, vec3(cutoff));
}

const float PI=3.1415926535898;

float easeInOutSine(float x){
	return -(cos(PI * x) - 1.0) / 2.0;
}

vec3 screenBlend(vec3 c0, vec3 c1, vec3 c2, vec3 c3){
	return c0+c1+c2+c3-(c0*c1*c2*c3);
}

float screenBlend(float c0, float c1, float c2, float c3){
	return c0+c1+c2+c3-(c0*c1*c2*c3);
}

void main() {
	vec3 avg=(colors[0]+colors[1]+colors[2]+colors[3])/4.0;

	float alpha0=easeInOutSine(clamp(1.0-distance(vTexCoord, points[0])*sizeFactor, 0.0, 1.0));
	float alpha1=easeInOutSine(clamp(1.0-distance(vTexCoord, points[1])*sizeFactor, 0.0, 1.0));
	float alpha2=easeInOutSine(clamp(1.0-distance(vTexCoord, points[2])*sizeFactor, 0.0, 1.0));
	float alpha3=easeInOutSine(clamp(1.0-distance(vTexCoord, points[3])*sizeFactor, 0.0, 1.0));

	float asum=max(1.0, alpha0+alpha1+alpha2+alpha3);

	vec3 col=screenBlend(colors[0]*(alpha0/asum), colors[1]*(alpha1/asum), colors[2]*(alpha2/asum), colors[3]*(alpha3/asum)) + avg*(1.0-(alpha0/asum+alpha1/asum+alpha2/asum+alpha3/asum));

	float gamma=0.43;
	float brightness0=pow(colors[0].x+colors[0].y+colors[0].z, gamma);
	float brightness1=pow(colors[1].x+colors[1].y+colors[1].z, gamma);
	float brightness2=pow(colors[2].x+colors[2].y+colors[2].z, gamma);
	float brightness3=pow(colors[3].x+colors[3].y+colors[3].z, gamma);
	float brightnessAvg=pow(avg.x+avg.y+avg.z, gamma);

	float brightness=screenBlend(brightness0*(alpha0/asum), brightness1*(alpha1/asum), brightness2*(alpha2/asum), brightness3*(alpha3/asum))+brightnessAvg*(1.0-(alpha0/asum+alpha1/asum+alpha2/asum+alpha3/asum));
	float intensity=pow(brightness, 1.0/gamma);
	col*=intensity/(col.x+col.y+col.z);

	gl_FragColor=vec4(fromLinear(col), 1.0);
}