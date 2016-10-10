#include <arrayfire.h>
#include <cstdint>
#include "SynMatrices.hpp"
#include "STDP.hpp"

#define POLAR GenericNeuron::Polarity


StandardSTDP::StandardSTDP(	const POLAR _srcPol,
							const POLAR _tarPol,
							const float _eta)
	: StandardSTDP(_srcPol, _tarPol, _eta, _hebbian) {}

StandardSTDP::StandardSTDP(	const POLAR _srcPol,
							const POLAR _tarPol,
							const float _eta,
							const bool _hebbian)
	: srcPol(_srcPol), tarPol(_tarPol), eta(_eta), heb(_hebbian)
{
	if (_srcPol && _tarPol) {
		w_p = DEF_EE_W_PLUS;
		w_m = DEF_EE_W_MINUS;
		tau_p = DEF_EE_TAU_P;
		tau_m = DEF_EE_TAU_M;
	} else if (_srcPol && !_tarPol) {
		w_p = DEF_EI_W_PLUS;
		w_m = DEF_EI_W_MINUS;
		tau_p = DEF_EI_TAU_P;
		tau_m = DEF_EI_TAU_M;
	} else if (!_srcPol && _tarPol) {
		w_p = DEF_IE_W_PLUS;
		w_m = DEF_IE_W_MINUS;
		tau_p = DEF_IE_TAU_P;
		tau_m = DEF_IE_TAU_M;
	} else {
		w_p = DEF_II_W_PLUS;
		w_m = DEF_II_W_MINUS;
		tau_p = DEF_II_TAU_P;
		tau_m = DEF_II_TAU_M;
	}
}

StandardSTDP(	const GenericNeuron::Polarity _srcPol,
				const GenericNeuron::Polarity _tarPol,
				const float _eta,
				const bool _hebbian,
				const float _w_p,
				const float _w_m,
				const float _tau_p
				const float _tau_m	)
	: srcPol(_srcPol), tarPol(_tarPol), eta(_eta),
	hebb(_hebbian)
{
	w_p = _w_p;
	w_m = _w_m;
	tau_p = _tau_p;
	tau_m = _tau_m;
}

array StandardSTDP::postTrigger(const array &lastPostSpk, const array &lastArr)
{
	if (hebb) {
		return eta * af::exp((lastArr - lastPostSpk)/tau_p) * w_p;
	} else {
		return -eta * af::exp((lastArr - lastPostSpk)/tau_m) * w_m;
	}
}

array StandardSTDP::preTriggerHebb(	const uint32_t lastPostSpk,
									const array &lastArr	)
{
	return -eta * af::exp((lastPostSpk - lastArr)/tau_m) * w_m;
}

array StandardSTDP::preTriggerAntiHebb(	const uint32_t lastPostSpk,
										const array &lastArr	)
{
	return eta * af::exp((lastPostSpk - lastArr)/tau_p) * w_p;	
}

