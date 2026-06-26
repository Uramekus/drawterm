#include "u.h"
#include "lib.h"
#include "dat.h"
#include "fns.h"
#include "error.h"
#include "devaudio.h"
#include <aaudio/AAudio.h>

static AAudioStream *out_stream = nil;
static AAudioStream *in_stream = nil;
static int cur_speed = 44100;

static void
open_stream(AAudioStream **streamp, aaudio_direction_t dir)
{
	AAudioStreamBuilder *builder;
	aaudio_result_t r;
	
	r = AAudio_createStreamBuilder(&builder);
	if(r != AAUDIO_OK) return;
	
	AAudioStreamBuilder_setDirection(builder, dir);
	AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
	AAudioStreamBuilder_setChannelCount(builder, 2);
	AAudioStreamBuilder_setSampleRate(builder, cur_speed);
	AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
	/* Setting data callback to NULL opens the stream in blocking (synchronous) mode */
	AAudioStreamBuilder_setDataCallback(builder, nil, nil);
	
	r = AAudioStreamBuilder_openStream(builder, streamp);
	AAudioStreamBuilder_delete(builder);
	
	if(*streamp){
		AAudioStream_requestStart(*streamp);
	}
}

void
audiodevopen(void)
{
	open_stream(&out_stream, AAUDIO_DIRECTION_OUTPUT);
	open_stream(&in_stream, AAUDIO_DIRECTION_INPUT);
	if(!out_stream && !in_stream)
		error("aaudio: failed to open streams");
}

void
audiodevclose(void)
{
	if(out_stream){
		AAudioStream_requestStop(out_stream);
		AAudioStream_close(out_stream);
		out_stream = nil;
	}
	if(in_stream){
		AAudioStream_requestStop(in_stream);
		AAudioStream_close(in_stream);
		in_stream = nil;
	}
}

int
audiodevread(void *a, int n)
{
	if(!in_stream)
		error("aaudio: no input stream");
		
	/* 16-bit stereo = 4 bytes per frame */
	int frames = n / 4;
	aaudio_result_t r = AAudioStream_read(in_stream, a, frames, 1000000000LL);
	
	if(r < 0)
		error("aaudio: read error");
		
	return r * 4;
}

int
audiodevwrite(void *a, int n)
{
	if(!out_stream)
		error("aaudio: no output stream");
		
	int frames = n / 4;
	aaudio_result_t r = AAudioStream_write(out_stream, a, frames, 1000000000LL);
	
	if(r < 0)
		error("aaudio: write error");
		
	return r * 4;
}

void
audiodevsetvol(int what, int left, int right)
{
	if(what == Vspeed){
		if(left > 0 && left <= 192000){
			cur_speed = left;
			/* Reopen streams with new sample rate */
			audiodevclose();
			audiodevopen();
		}
	}
}

void
audiodevgetvol(int what, int *left, int *right)
{
	if(what == Vspeed){
		*left = cur_speed;
		*right = cur_speed;
	}
}
